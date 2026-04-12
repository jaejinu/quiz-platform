package com.quiz.domain.participant;

import com.quiz.common.exception.InvalidGameStateException;
import com.quiz.domain.game.GameSnapshotService;
import com.quiz.domain.leaderboard.LeaderboardKeys;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.RoomService;
import com.quiz.domain.room.RoomStatus;
import com.quiz.domain.user.UserRepository;
import com.quiz.infra.redis.RoomEventPublisher;
import com.quiz.infra.redis.RoomEventType;
import com.quiz.infra.websocket.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipantService {

    private static final String PENDING_CLEANUPS_KEY = "pending_cleanups";
    private static final long GRACE_PERIOD_MS = 30_000L;
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final ParticipantRepository participantRepository;
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final RoomEventPublisher roomEventPublisher;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final GameSnapshotService gameSnapshotService;

    @Transactional
    public void join(Long roomId, AuthPrincipal principal, String sessionId) {
        QuizRoom room = roomService.getEntityOrThrow(roomId);
        if (room.getStatus() != RoomStatus.WAITING && room.getStatus() != RoomStatus.ACTIVE) {
            throw new InvalidGameStateException("ROOM_NOT_JOINABLE");
        }

        Long userId = principal.userId();
        String nickname = principal.nickname();

        Participant participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .map(existing -> {
                    existing.reconnect(sessionId);
                    log.debug("participant reconnected roomId={} userId={} sessionId={}", roomId, userId, sessionId);
                    return existing;
                })
                .orElseGet(() -> {
                    long connected = participantRepository.countByRoomIdAndStatus(roomId, ParticipantStatus.CONNECTED);
                    if (connected >= room.getMaxPlayers()) {
                        throw new InvalidGameStateException("ROOM_FULL");
                    }
                    Participant created = Participant.builder()
                            .roomId(roomId)
                            .userId(userId)
                            .sessionId(sessionId)
                            .build();
                    log.info("participant joined roomId={} userId={} sessionId={}", roomId, userId, sessionId);
                    return participantRepository.save(created);
                });

        stringRedisTemplate.opsForValue().set(
                LeaderboardKeys.sessionKey(sessionId),
                userId + ":" + roomId,
                SESSION_TTL
        );

        long count = participantRepository.countByRoomIdAndStatus(roomId, ParticipantStatus.CONNECTED);
        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.PLAYER_JOINED, Map.of(
                "userId", userId,
                "nickname", nickname,
                "participantCount", count
        ));

        gameSnapshotService.sendSnapshot(principal, roomId);
    }

    @Transactional
    public void leave(Long roomId, Long userId) {
        Participant participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new InvalidGameStateException("NOT_A_PARTICIPANT"));

        String sessionId = participant.getSessionId();
        participant.leave();

        if (sessionId != null) {
            stringRedisTemplate.delete(LeaderboardKeys.sessionKey(sessionId));
        }

        long count = participantRepository.countByRoomIdAndStatus(roomId, ParticipantStatus.CONNECTED);
        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.PLAYER_LEFT, Map.of(
                "userId", userId,
                "participantCount", count
        ));
        log.info("participant left roomId={} userId={}", roomId, userId);
    }

    @Transactional
    public void disconnect(String sessionId) {
        participantRepository.findBySessionId(sessionId).ifPresent(p -> {
            p.disconnect();
            long expireAt = System.currentTimeMillis() + GRACE_PERIOD_MS;
            stringRedisTemplate.opsForZSet().add(PENDING_CLEANUPS_KEY, sessionId, expireAt);
            log.debug("participant disconnected (grace) roomId={} userId={} sessionId={}",
                    p.getRoomId(), p.getUserId(), sessionId);
        });
    }

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        Set<String> expired = stringRedisTemplate.opsForZSet().rangeByScore(PENDING_CLEANUPS_KEY, 0, now);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (String sessionId : expired) {
            try {
                participantRepository.findBySessionId(sessionId).ifPresent(p -> {
                    if (p.getStatus() == ParticipantStatus.DISCONNECTED) {
                        Long roomId = p.getRoomId();
                        Long userId = p.getUserId();
                        p.leave();
                        stringRedisTemplate.delete(LeaderboardKeys.sessionKey(sessionId));
                        long count = participantRepository.countByRoomIdAndStatus(roomId, ParticipantStatus.CONNECTED);
                        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.PLAYER_LEFT, Map.of(
                                "userId", userId,
                                "participantCount", count
                        ));
                        log.info("participant cleaned up after grace roomId={} userId={} sessionId={}",
                                roomId, userId, sessionId);
                    }
                });
            } finally {
                stringRedisTemplate.opsForZSet().remove(PENDING_CLEANUPS_KEY, sessionId);
            }
        }
    }

    public Participant getByRoomAndUserOrThrow(Long roomId, Long userId) {
        return participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new InvalidGameStateException("NOT_A_PARTICIPANT"));
    }
}

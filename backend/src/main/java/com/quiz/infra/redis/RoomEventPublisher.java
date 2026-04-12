package com.quiz.infra.redis;

import com.quiz.domain.leaderboard.LeaderboardKeys;
import com.quiz.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/room/";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate simpMessagingTemplate;
    @Qualifier("publisherId")
    private final String publisherId;
    private final EventMetrics eventMetrics;

    public void publish(Long roomId, RoomEventType type, Map<String, Object> payload) {
        RoomEvent event = RoomEvent.of(type, roomId, publisherId, payload);
        try {
            // 1단계: 로컬 STOMP 세션에 즉시 전달
            simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + roomId, event);
            // 2단계: 원격 서버에게 Redis Pub/Sub 브리지
            redisTemplate.convertAndSend(LeaderboardKeys.roomChannel(roomId), event);
            eventMetrics.incrementPublished(type);
            log.debug("published roomId={} type={} publisherId={}", roomId, type, publisherId);
        } catch (Exception e) {
            log.warn("failed to publish room event roomId={} type={} publisherId={}: {}",
                    roomId, type, publisherId, e.getMessage(), e);
        }
    }

    /**
     * 활성 트랜잭션 안이면 커밋 이후에 발행, 아니면 즉시 발행.
     * "save 실패했는데 이벤트만 나감"을 방지.
     */
    public void publishAfterCommit(Long roomId, RoomEventType type, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(roomId, type, payload);
                }
            });
        } else {
            publish(roomId, type, payload);
        }
    }
}

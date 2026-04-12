package com.quiz.domain.room;

import com.quiz.api.dto.CreateRoomRequest;
import com.quiz.api.dto.RoomResponse;
import com.quiz.common.exception.RoomNotFoundException;
import com.quiz.common.exception.UnauthorizedException;
import com.quiz.domain.participant.ParticipantRepository;
import com.quiz.domain.participant.ParticipantStatus;
import com.quiz.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final QuizRoomRepository quizRoomRepository;
    private final UserRepository userRepository;
    private final RoomCodeGenerator roomCodeGenerator;
    private final ParticipantRepository participantRepository;

    @Transactional
    public Long create(CreateRoomRequest req) {
        log.debug("create room title={} hostId={}", req.title(), req.hostId());
        userRepository.findById(req.hostId())
            .orElseThrow(() -> new UnauthorizedException("존재하지 않는 사용자입니다. hostId=" + req.hostId()));

        String code = roomCodeGenerator.generate();
        QuizRoom room = QuizRoom.builder()
            .code(code)
            .hostId(req.hostId())
            .title(req.title())
            .maxPlayers(req.maxPlayers())
            .defaultTimeLimit(req.defaultTimeLimit())
            .build();
        quizRoomRepository.save(room);
        log.debug("room created id={} code={}", room.getId(), code);
        return room.getId();
    }

    public RoomResponse findById(Long id) {
        log.debug("findById id={}", id);
        QuizRoom room = getEntityOrThrow(id);
        return toResponse(room);
    }

    public RoomResponse findByCode(String code) {
        log.debug("findByCode code={}", code);
        QuizRoom room = quizRoomRepository.findByCode(code)
            .orElseThrow(() -> new RoomNotFoundException(code));
        return toResponse(room);
    }

    public QuizRoom getEntityOrThrow(Long id) {
        return quizRoomRepository.findById(id)
            .orElseThrow(() -> new RoomNotFoundException(id));
    }

    public void validateHost(Long roomId, Long userId) {
        QuizRoom room = getEntityOrThrow(roomId);
        if (!Objects.equals(room.getHostId(), userId)) {
            throw new UnauthorizedException("HOST 권한이 필요합니다. roomId=" + roomId + " userId=" + userId);
        }
    }

    private RoomResponse toResponse(QuizRoom room) {
        long participantCount = participantRepository
            .countByRoomIdAndStatus(room.getId(), ParticipantStatus.CONNECTED);
        return new RoomResponse(
            room.getId(),
            room.getCode(),
            room.getTitle(),
            room.getStatus().name(),
            room.getHostId(),
            room.getMaxPlayers(),
            room.getDefaultTimeLimit(),
            (int) participantCount
        );
    }
}

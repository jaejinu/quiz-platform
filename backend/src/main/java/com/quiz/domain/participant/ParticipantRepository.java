package com.quiz.domain.participant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByRoomIdAndUserId(Long roomId, Long userId);

    Optional<Participant> findBySessionId(String sessionId);

    List<Participant> findAllByRoomId(Long roomId);

    List<Participant> findAllByRoomIdAndStatus(Long roomId, ParticipantStatus status);

    long countByRoomIdAndStatus(Long roomId, ParticipantStatus status);
}

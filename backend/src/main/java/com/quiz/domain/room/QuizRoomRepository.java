package com.quiz.domain.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRoomRepository extends JpaRepository<QuizRoom, Long> {

    Optional<QuizRoom> findByCode(String code);

    boolean existsByCode(String code);

    List<QuizRoom> findAllByStatus(RoomStatus status);

    List<QuizRoom> findAllByHostId(Long hostId);
}

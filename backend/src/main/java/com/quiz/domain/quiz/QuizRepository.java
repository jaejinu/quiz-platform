package com.quiz.domain.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findAllByRoomIdOrderByOrderIndexAsc(Long roomId);

    Optional<Quiz> findByRoomIdAndOrderIndex(Long roomId, int orderIndex);

    long countByRoomId(Long roomId);
}

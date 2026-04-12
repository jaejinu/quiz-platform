package com.quiz.domain.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    Optional<Answer> findByParticipantIdAndQuizId(Long participantId, Long quizId);

    List<Answer> findAllByParticipantId(Long participantId);

    List<Answer> findAllByQuizId(Long quizId);

    long countByQuizIdAndIsCorrectTrue(Long quizId);
}

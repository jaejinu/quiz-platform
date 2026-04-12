package com.quiz.domain.answer;

import com.quiz.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "answers",
    uniqueConstraints = {
        // 한 참가자가 한 퀴즈에 중복 제출 금지
        @UniqueConstraint(name = "uk_answers_participant_quiz", columnNames = {"participant_id", "quiz_id"})
    },
    indexes = {
        @Index(name = "idx_answers_quiz", columnList = "quiz_id"),
        @Index(name = "idx_answers_participant", columnList = "participant_id")
    }
)
public class Answer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(nullable = false, length = 500)
    private String answer;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "response_time_ms", nullable = false)
    private long responseTimeMs;

    @Column(nullable = false)
    private int score;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Builder
    private Answer(Long participantId, Long quizId, String answer, boolean isCorrect,
                   long responseTimeMs, int score) {
        this.participantId = participantId;
        this.quizId = quizId;
        this.answer = answer;
        this.isCorrect = isCorrect;
        this.responseTimeMs = responseTimeMs;
        this.score = score;
        this.submittedAt = Instant.now();
    }
}

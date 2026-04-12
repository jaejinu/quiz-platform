package com.quiz.domain.result;

/**
 * 퀴즈 한 문제의 집계 통계.
 *
 * @param accuracyRate 0.0 ~ 1.0
 */
public record QuizStats(
    Long quizId,
    int orderIndex,
    String question,
    String correctAnswer,
    String type,
    long totalAnswers,
    long correctAnswers,
    double accuracyRate,
    double avgResponseTimeMs
) {

    public long incorrectCount() {
        return totalAnswers - correctAnswers;
    }
}

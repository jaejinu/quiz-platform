package com.quiz.domain.game;

public record GameStateSnapshot(
    Long currentQuizId,
    Integer currentQuizIndex,
    Long startedAtMs,
    Long deadlineMs,
    boolean active
) {
}

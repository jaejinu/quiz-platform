package com.quiz.domain.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quiz.domain.leaderboard.LeaderboardEntry;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSnapshot(
    Long roomId,
    String status,
    Object currentQuiz,
    long remainingMs,
    List<LeaderboardEntry> leaderboard
) {
}

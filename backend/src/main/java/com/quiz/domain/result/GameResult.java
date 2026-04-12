package com.quiz.domain.result;

import java.time.Instant;
import java.util.List;

/**
 * 한 방의 전체 게임 결과 집계.
 */
public record GameResult(
    Long roomId,
    String roomCode,
    String roomTitle,
    String hostNickname,
    Instant startedAt,
    Instant finishedAt,
    int totalParticipants,
    int totalQuizzes,
    List<LeaderboardSnapshot> leaderboard,
    List<QuizStats> quizStats,
    List<QuizStats> worstThree
) {
}

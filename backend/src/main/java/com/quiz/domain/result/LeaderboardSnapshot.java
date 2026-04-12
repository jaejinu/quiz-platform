package com.quiz.domain.result;

/**
 * 리더보드 한 행의 스냅샷.
 *
 * @param rank 1-based 순위
 */
public record LeaderboardSnapshot(Long userId, String nickname, int score, int rank) {
}

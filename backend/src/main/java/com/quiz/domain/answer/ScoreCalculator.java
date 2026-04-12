package com.quiz.domain.answer;

/**
 * 점수 계산기.
 * 정답이면 응답시간에 따라 최대 1000점, 오답이면 0점.
 * score = max * (1 - (responseTimeMs / (timeLimitSec * 1000)))
 * 응답시간이 제한시간을 초과하면 0점 반환.
 */
public final class ScoreCalculator {

    public static final int MAX_SCORE = 1000;

    private ScoreCalculator() {}

    public static int calculate(boolean correct, long responseTimeMs, int timeLimitSec) {
        if (!correct) {
            return 0;
        }
        if (timeLimitSec <= 0) {
            return MAX_SCORE;
        }
        long limitMs = timeLimitSec * 1000L;
        if (responseTimeMs >= limitMs) {
            return 0;
        }
        double ratio = 1.0 - ((double) responseTimeMs / limitMs);
        return (int) Math.round(MAX_SCORE * ratio);
    }
}

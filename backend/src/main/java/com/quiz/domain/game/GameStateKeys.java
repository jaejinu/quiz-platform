package com.quiz.domain.game;

import java.util.List;

public final class GameStateKeys {

    private GameStateKeys() {}

    public static String currentQuizId(Long roomId) {
        return "game:" + roomId + ":currentQuizId";
    }

    public static String currentQuizIndex(Long roomId) {
        return "game:" + roomId + ":currentQuizIndex";
    }

    public static String quizStartedAt(Long roomId) {
        return "game:" + roomId + ":quizStartedAt";
    }

    public static String quizDeadline(Long roomId) {
        return "game:" + roomId + ":quizDeadline";
    }

    public static String advanceLock(Long roomId, Long quizId) {
        return "game:" + roomId + ":advance:lock:" + quizId;
    }

    public static String answerCount(Long roomId, Long quizId) {
        return "game:" + roomId + ":answers:" + quizId;
    }

    public static final String ACTIVE_ROOMS = "game:active_rooms";

    /**
     * clear() 시 DEL 대상 state key 목록.
     * advanceLock / answerCount 은 quizId별로 여러 개이고 TTL로 자동 만료되므로 제외.
     */
    public static List<String> allGameKeys(Long roomId) {
        return List.of(
            currentQuizId(roomId),
            currentQuizIndex(roomId),
            quizStartedAt(roomId),
            quizDeadline(roomId)
        );
    }
}

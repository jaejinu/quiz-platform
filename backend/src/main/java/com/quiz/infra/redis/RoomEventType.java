package com.quiz.infra.redis;

public enum RoomEventType {
    PLAYER_JOINED,
    PLAYER_LEFT,
    GAME_STARTED,
    QUIZ_PUSHED,
    ANSWER_SUBMITTED,
    LEADERBOARD_UPDATED,
    GAME_FINISHED
}

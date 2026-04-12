package com.quiz.common.exception;

public class InvalidGameStateException extends QuizException {

    public static final String CODE = "INVALID_GAME_STATE";

    public InvalidGameStateException(String reason) {
        super(CODE, reason);
    }
}

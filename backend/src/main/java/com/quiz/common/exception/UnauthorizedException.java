package com.quiz.common.exception;

public class UnauthorizedException extends QuizException {

    public static final String CODE = "UNAUTHORIZED";

    public UnauthorizedException(String reason) {
        super(CODE, reason);
    }
}

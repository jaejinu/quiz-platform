package com.quiz.common.exception;

import lombok.Getter;

@Getter
public class QuizException extends RuntimeException {

    private final String code;

    public QuizException(String code, String message) {
        super(message);
        this.code = code;
    }
}

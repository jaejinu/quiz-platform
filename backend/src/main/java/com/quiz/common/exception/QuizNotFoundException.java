package com.quiz.common.exception;

public class QuizNotFoundException extends QuizException {

    public static final String CODE = "QUIZ_NOT_FOUND";

    public QuizNotFoundException(Long id) {
        super(CODE, "퀴즈를 찾을 수 없습니다. id=" + id);
    }
}

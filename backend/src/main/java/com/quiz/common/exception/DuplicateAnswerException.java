package com.quiz.common.exception;

public class DuplicateAnswerException extends QuizException {

    public static final String CODE = "DUPLICATE_ANSWER";

    public DuplicateAnswerException(Long participantId, Long quizId) {
        super(CODE, "이미 답변을 제출했습니다. participantId=" + participantId + ", quizId=" + quizId);
    }
}

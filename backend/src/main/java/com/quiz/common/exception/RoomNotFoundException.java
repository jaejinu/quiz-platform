package com.quiz.common.exception;

public class RoomNotFoundException extends QuizException {

    public static final String CODE = "ROOM_NOT_FOUND";

    public RoomNotFoundException(Long id) {
        super(CODE, "방을 찾을 수 없습니다. id=" + id);
    }

    public RoomNotFoundException(String code) {
        super(CODE, "방을 찾을 수 없습니다. code=" + code);
    }
}

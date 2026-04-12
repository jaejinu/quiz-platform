package com.quiz.common.exception;

public class RoomCodeGenerationFailedException extends QuizException {

    public static final String CODE = "ROOM_CODE_GEN_FAILED";

    public RoomCodeGenerationFailedException() {
        super(CODE, "방 코드 생성에 실패했습니다. 잠시 후 다시 시도하세요.");
    }
}

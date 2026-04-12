package com.quiz.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방 생성 요청.
 *
 * <p>hostId는 Step 4 임시 필드. Step 5에서 JWT 도입 시 Principal로부터 추출하고 이 필드는 제거된다.
 */
public record CreateRoomRequest(
    @NotBlank @Size(max = 100) String title,
    @Min(2) @Max(500) int maxPlayers,
    @Min(5) @Max(300) int defaultTimeLimit,
    @NotNull Long hostId
) {
}

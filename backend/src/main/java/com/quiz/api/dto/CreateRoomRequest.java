package com.quiz.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 방 생성 요청.
 *
 * <p>hostId는 JWT Principal에서 추출한다(Step 7). DTO에 포함하지 않는다.
 */
public record CreateRoomRequest(
    @NotBlank @Size(max = 100) String title,
    @Min(2) @Max(500) int maxPlayers,
    @Min(5) @Max(300) int defaultTimeLimit
) {
}

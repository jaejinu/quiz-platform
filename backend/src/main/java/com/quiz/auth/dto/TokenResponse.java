package com.quiz.auth.dto;

import java.time.Instant;

public record TokenResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    UserInfo user
) {

    public record UserInfo(Long id, String nickname, String role) {
    }
}

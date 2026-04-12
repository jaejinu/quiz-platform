package com.quiz.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 로그인 성공/실패 후 프론트엔드로 redirect할 base URL 설정.
 *
 * <p>{@code quiz.auth.post-login-redirect} 값을 받아 토큰/에러 정보를 fragment(#)에 붙여 리다이렉트한다.
 */
@ConfigurationProperties(prefix = "quiz.auth")
public record AuthProperties(String postLoginRedirect) {

    public AuthProperties {
        postLoginRedirect = postLoginRedirect == null || postLoginRedirect.isBlank()
            ? "http://localhost:5173/auth/callback"
            : postLoginRedirect;
    }
}

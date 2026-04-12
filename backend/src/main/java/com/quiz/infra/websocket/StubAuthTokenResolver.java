package com.quiz.infra.websocket;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 통합 테스트용 스텁 리졸버.
 *
 * <p>토큰 포맷: {@code "Bearer stub:<userId>:<nickname>:<role>"}.
 * test 프로파일에서만 등록된다 (local/prod 는 {@code JwtAuthTokenResolver} 사용).
 */
@Slf4j
@Component
@Profile("test")
public class StubAuthTokenResolver implements AuthTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String STUB_PREFIX = "stub:";

    @Override
    public Optional<AuthPrincipal> resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!token.startsWith(STUB_PREFIX)) {
            return Optional.empty();
        }
        String payload = token.substring(STUB_PREFIX.length());
        String[] parts = payload.split(":", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            Long userId = Long.parseLong(parts[0].trim());
            String nickname = parts[1].trim();
            String role = parts[2].trim();
            if (nickname.isEmpty() || role.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(userId, nickname, role));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

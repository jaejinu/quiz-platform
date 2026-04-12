package com.quiz.auth;

import com.quiz.infra.websocket.AuthPrincipal;
import com.quiz.infra.websocket.AuthTokenResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 실 JWT 리졸버. test 프로파일이 아닐 때만 등록된다.
 * test 프로파일에선 {@code StubAuthTokenResolver}가 같은 인터페이스 빈으로 등록된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class JwtAuthTokenResolver implements AuthTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Optional<AuthPrincipal> resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return jwtTokenProvider.parse(token);
    }
}

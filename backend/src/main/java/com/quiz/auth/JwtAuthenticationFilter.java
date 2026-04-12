package com.quiz.auth;

import com.quiz.infra.websocket.AuthPrincipal;
import com.quiz.infra.websocket.AuthTokenResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authorization 헤더를 {@link AuthTokenResolver}에 위임해 해석하고 SecurityContext에 주입.
 *
 * <p>헤더가 없거나 해석 실패면 체인을 그대로 통과시킨다. 401은 인증이 필요한 엔드포인트에서
 * {@link JwtAuthenticationEntryPoint}가 판정한다.
 *
 * <p>프로파일에 따라 주입되는 리졸버가 달라진다: local/prod에선 {@code JwtAuthTokenResolver},
 * test에선 {@code StubAuthTokenResolver}. STOMP 경로 (ChannelInterceptor) 와 REST 경로가
 * 같은 리졸버 빈을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenResolver authTokenResolver;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Optional<AuthPrincipal> principal = authTokenResolver.resolve(header);
            if (principal.isPresent()) {
                AuthPrincipal auth = principal.get();
                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    auth,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + auth.role()))
                );
                SecurityContextHolder.getContext().setAuthentication(token);
            }
        } catch (Exception e) {
            log.warn("Authorization resolution failed (continuing as anonymous): {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

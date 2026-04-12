package com.quiz.auth;

import com.quiz.domain.user.UserRole;
import com.quiz.infra.websocket.AuthPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 발급/파싱 공용 컴포넌트.
 *
 * <p>서명: HS256. 만료 시 parse는 {@link Optional#empty()} 반환 (예외 외부 전파 안 함).
 * subject=userId(string), claims: nickname, role. issuer는 설정값.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtTokenProvider(
        @Value("${quiz.security.jwt.secret}") String secret,
        @Value("${quiz.security.jwt.access-token-ttl}") Duration ttl,
        @Value("${quiz.security.jwt.issuer}") String issuer
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("quiz.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String issue(Long userId, String nickname, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        return Jwts.builder()
            .subject(userId.toString())
            .claim("nickname", nickname)
            .claim("role", role.name())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String nickname = claims.get("nickname", String.class);
            String role = claims.get("role", String.class);
            if (nickname == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(userId, nickname, role));
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Instant expiresAt(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getExpiration()
            .toInstant();
    }

    public Duration getTtl() {
        return ttl;
    }
}

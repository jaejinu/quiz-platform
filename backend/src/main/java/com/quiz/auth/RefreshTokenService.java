package com.quiz.auth;

import com.quiz.common.exception.UnauthorizedException;
import com.quiz.domain.user.RefreshToken;
import com.quiz.domain.user.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        @Value("${quiz.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /**
     * 새 refresh token 발급. 원본 문자열은 클라이언트에게만 반환.
     */
    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        String rawToken = UUID.randomUUID().toString();
        String hash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(refreshTokenTtl);

        RefreshToken entity = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hash)
            .expiresAt(expiresAt)
            .build();
        refreshTokenRepository.save(entity);

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * Refresh token rotation: 기존 폐기 + 새 발급.
     * 이미 폐기된 토큰이면 탈취로 간주하여 해당 사용자의 전체 토큰 폐기.
     */
    @Transactional
    public RotateResult rotate(String rawToken) {
        String hash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("유효하지 않은 refresh token"));

        if (token.isRevoked()) {
            // 탈취 감지: 해당 사용자의 모든 토큰 폐기
            int revoked = refreshTokenRepository.revokeAllByUserId(token.getUserId());
            log.warn("refresh token 재사용 감지 — userId={} 전체 {}개 폐기", token.getUserId(), revoked);
            throw new UnauthorizedException("refresh token이 재사용되었습니다. 다시 로그인해주세요");
        }

        if (!token.isUsable()) {
            throw new UnauthorizedException("만료된 refresh token");
        }

        token.revoke();
        IssuedRefreshToken newToken = issue(token.getUserId());
        return new RotateResult(newToken.rawToken(), newToken.expiresAt(), token.getUserId());
    }

    /**
     * 토큰 폐기 (로그아웃). 멱등 — 없는 토큰이어도 예외 없음.
     */
    @Transactional
    public void revoke(String rawToken) {
        String hash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash)
            .ifPresent(RefreshToken::revoke);
    }

    /**
     * 만료/폐기 토큰 정리. 매일 새벽 3시 실행.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.info("refresh token 정리 완료: {}개 삭제", deleted);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}
    public record RotateResult(String rawToken, Instant expiresAt, Long userId) {}
}

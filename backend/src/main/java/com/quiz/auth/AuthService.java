package com.quiz.auth;

import com.quiz.auth.dto.LoginRequest;
import com.quiz.auth.dto.SignupRequest;
import com.quiz.auth.dto.TokenResponse;
import com.quiz.common.exception.QuizException;
import com.quiz.common.exception.UnauthorizedException;
import com.quiz.domain.user.OAuthProvider;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import com.quiz.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 이메일/비밀번호 회원가입/로그인 + refresh token 갱신/로그아웃.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    public static final String EMAIL_DUPLICATE = "EMAIL_DUPLICATE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public TokenResponse signup(SignupRequest req) {
        log.debug("signup email={}", req.email());
        if (userRepository.existsByEmail(req.email())) {
            throw new QuizException(EMAIL_DUPLICATE, "이미 가입된 이메일입니다");
        }
        User user = User.builder()
            .email(req.email())
            .nickname(req.nickname())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(UserRole.PLAYER)
            .oauthProvider(OAuthProvider.LOCAL)
            .build();
        userRepository.save(user);
        log.info("signup success userId={} email={}", user.getId(), user.getEmail());
        return buildResponse(user);
    }

    public TokenResponse login(LoginRequest req) {
        log.debug("login email={}", req.email());
        User user = userRepository.findByEmail(req.email())
            .orElseThrow(() -> new UnauthorizedException("잘못된 이메일 또는 비밀번호"));
        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("잘못된 이메일 또는 비밀번호");
        }
        log.info("login success userId={} email={}", user.getId(), user.getEmail());
        return buildResponse(user);
    }

    /**
     * Refresh token으로 새 access token + 새 refresh token 발급 (rotation).
     */
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotateResult result = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(result.userId())
            .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다"));

        String accessToken = jwtTokenProvider.issue(user.getId(), user.getNickname(), user.getRole());
        Instant expiresAt = Instant.now().plus(jwtTokenProvider.getTtl());
        return new TokenResponse(
            accessToken, "Bearer", expiresAt,
            result.rawToken(), result.expiresAt(),
            new TokenResponse.UserInfo(user.getId(), user.getNickname(), user.getRole().name())
        );
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenResponse buildResponse(User user) {
        String token = jwtTokenProvider.issue(user.getId(), user.getNickname(), user.getRole());
        Instant expiresAt = Instant.now().plus(jwtTokenProvider.getTtl());
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
        return new TokenResponse(
            token, "Bearer", expiresAt,
            refresh.rawToken(), refresh.expiresAt(),
            new TokenResponse.UserInfo(user.getId(), user.getNickname(), user.getRole().name())
        );
    }
}

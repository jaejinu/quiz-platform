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
 * 이메일/비밀번호 회원가입/로그인.
 *
 * <p>회원가입 기본 role은 {@link UserRole#PLAYER}. HOST 계정은 {@code HostSeedRunner}가 시드하거나
 * DB에서 직접 부여한다 (Step 7 범위에선 승격 엔드포인트 없음).
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

    private TokenResponse buildResponse(User user) {
        String token = jwtTokenProvider.issue(user.getId(), user.getNickname(), user.getRole());
        Instant expiresAt = Instant.now().plus(jwtTokenProvider.getTtl());
        return new TokenResponse(
            token,
            "Bearer",
            expiresAt,
            new TokenResponse.UserInfo(user.getId(), user.getNickname(), user.getRole().name())
        );
    }
}

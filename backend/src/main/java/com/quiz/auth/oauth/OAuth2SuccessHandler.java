package com.quiz.auth.oauth;

import com.quiz.auth.JwtTokenProvider;
import com.quiz.auth.RefreshTokenService;
import com.quiz.common.exception.QuizException;
import com.quiz.domain.user.OAuthProvider;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import com.quiz.domain.user.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * GitHub OAuth2 로그인 성공 시 User upsert → JWT 발급 → 프론트로 fragment redirect.
 *
 * <p>에러는 예외로 던지지 않고 fragment {@code #error=...}로 redirect해 Agent B(프론트)가 파싱하게 한다.
 *
 * <p>Redirect fragment 포맷:
 * <ul>
 *   <li>성공: {@code #token=<jwt>&userId=<id>&nickname=<nick>&role=<role>}</li>
 *   <li>실패: {@code #error=<code>} — code: {@code email_required}, {@code email_conflict},
 *       {@code nickname_conflict}, {@code oauth_provider_unsupported}, {@code oauth_internal}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            log.warn("unexpected authentication type: {}", authentication.getClass());
            sendErrorRedirect(request, response, "oauth_internal");
            return;
        }

        String registrationId = token.getAuthorizedClientRegistrationId();
        if (!"github".equalsIgnoreCase(registrationId)) {
            log.warn("unsupported oauth2 provider: {}", registrationId);
            sendErrorRedirect(request, response, "oauth_provider_unsupported");
            return;
        }

        OAuth2User oauth = token.getPrincipal();

        Object idAttr = oauth.getAttribute("id");
        String githubId = idAttr == null ? null : String.valueOf(idAttr);
        String login = oauth.getAttribute("login");
        String email = oauth.getAttribute("email");

        if (githubId == null || login == null) {
            log.warn("github attributes missing id/login: id={}, login={}", githubId, login);
            sendErrorRedirect(request, response, "oauth_internal");
            return;
        }

        try {
            User user = resolveOrCreateUser(githubId, login, email);
            String jwt = jwtTokenProvider.issue(user.getId(), user.getNickname(), user.getRole());
            RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
            String url = authProperties.postLoginRedirect()
                + "#token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8)
                + "&userId=" + user.getId()
                + "&nickname=" + URLEncoder.encode(user.getNickname(), StandardCharsets.UTF_8)
                + "&role=" + user.getRole().name()
                + "&refreshToken=" + URLEncoder.encode(refresh.rawToken(), StandardCharsets.UTF_8)
                + "&refreshTokenExpiresAt=" + URLEncoder.encode(refresh.expiresAt().toString(), StandardCharsets.UTF_8);

            log.info("github oauth2 login success userId={} nickname={}", user.getId(), user.getNickname());
            redirectStrategy.sendRedirect(request, response, url);
        } catch (QuizException e) {
            log.warn("github oauth2 login failed code={} msg={}", e.getCode(), e.getMessage());
            sendErrorRedirect(request, response, e.getCode());
        } catch (DataIntegrityViolationException e) {
            log.warn("github oauth2 login data integrity violation", e);
            sendErrorRedirect(request, response, "oauth_internal");
        } catch (Exception e) {
            log.error("github oauth2 login unexpected error", e);
            sendErrorRedirect(request, response, "oauth_internal");
        }
    }

    private User resolveOrCreateUser(String githubId, String login, String email) {
        Optional<User> existing = userRepository.findByOauthProviderAndOauthId(OAuthProvider.GITHUB, githubId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 신규 가입: email 필수
        if (email == null || email.isBlank()) {
            throw new QuizException("email_required", "GitHub public email이 필요합니다");
        }

        // email 충돌: 이미 LOCAL(또는 다른 provider)으로 가입한 계정
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent() && byEmail.get().getOauthProvider() != OAuthProvider.GITHUB) {
            throw new QuizException("email_conflict", "해당 이메일은 다른 로그인 수단으로 이미 가입되어 있습니다");
        }

        // nickname: users 테이블에 unique 제약이 없으므로 login 그대로 사용 (중복 허용)
        User newUser = User.builder()
            .email(email)
            .nickname(login)
            .role(UserRole.PLAYER)
            .oauthProvider(OAuthProvider.GITHUB)
            .oauthId(githubId)
            .build();
        return userRepository.save(newUser);
    }

    private void sendErrorRedirect(HttpServletRequest request,
                                   HttpServletResponse response,
                                   String errorCode) throws IOException {
        String url = authProperties.postLoginRedirect()
            + "#error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        redirectStrategy.sendRedirect(request, response, url);
    }
}

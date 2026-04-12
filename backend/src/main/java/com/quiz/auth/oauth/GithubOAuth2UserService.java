package com.quiz.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub /user 응답에 email이 비어있을 때 /user/emails를 추가 호출해 primary+verified 이메일을 채워 넣는다.
 *
 * <p>이메일을 끝내 못 구하면 email 속성 없이 그대로 반환하고, 이후 {@link OAuth2SuccessHandler}가
 * {@code email_required}로 redirect한다.
 */
@Slf4j
@Component
public class GithubOAuth2UserService extends DefaultOAuth2UserService {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String NAME_ATTRIBUTE_KEY = "id";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equalsIgnoreCase(registrationId)) {
            return user;
        }

        Object emailAttr = user.getAttribute("email");
        String email = emailAttr instanceof String s ? s : null;
        if (email != null && !email.isBlank()) {
            return rebuildWithAttributes(user, user.getAttributes());
        }

        String accessToken = userRequest.getAccessToken().getTokenValue();
        String fetched = fetchPrimaryVerifiedEmail(accessToken);

        Map<String, Object> attrs = new HashMap<>(user.getAttributes());
        if (fetched != null) {
            attrs.put("email", fetched);
        }
        return rebuildWithAttributes(user, attrs);
    }

    private OAuth2User rebuildWithAttributes(OAuth2User user, Map<String, Object> attrs) {
        return new DefaultOAuth2User(user.getAuthorities(), attrs, NAME_ATTRIBUTE_KEY);
    }

    private String fetchPrimaryVerifiedEmail(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                GITHUB_EMAILS_URL,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {
                }
            );
            List<Map<String, Object>> body = resp.getBody();
            if (body == null) {
                return null;
            }
            for (Map<String, Object> item : body) {
                boolean primary = Boolean.TRUE.equals(item.get("primary"));
                boolean verified = Boolean.TRUE.equals(item.get("verified"));
                Object e = item.get("email");
                if (primary && verified && e instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            // fallback: verified 중 첫 번째
            for (Map<String, Object> item : body) {
                boolean verified = Boolean.TRUE.equals(item.get("verified"));
                Object e = item.get("email");
                if (verified && e instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            return null;
        } catch (RestClientException e) {
            log.warn("failed to fetch github user emails: {}", e.getMessage());
            OAuth2Error err = new OAuth2Error(
                "github_emails_fetch_failed",
                "Failed to load GitHub user emails: " + e.getMessage(),
                null
            );
            // 속성 자체를 못 채우면 null 반환하여 success handler가 email_required로 처리하도록 한다.
            // OAuth2AuthenticationException으로 던지면 FailureHandler가 oauth_failed로 처리.
            log.debug("swallow github emails error and proceed without email: {}", err);
            return null;
        }
    }
}

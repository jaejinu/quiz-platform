package com.quiz.config;

import com.quiz.auth.JwtAuthenticationEntryPoint;
import com.quiz.auth.JwtAuthenticationFilter;
import com.quiz.auth.oauth.AuthProperties;
import com.quiz.auth.oauth.GithubOAuth2UserService;
import com.quiz.auth.oauth.OAuth2FailureHandler;
import com.quiz.auth.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT + OAuth2(GitHub) 혼합 보안 설정.
 *
 * <p>API 요청은 {@link JwtAuthenticationFilter}가 Authorization 헤더를 파싱해 SecurityContext에
 * {@code AuthPrincipal}을 주입한다. 실패 시 {@link JwtAuthenticationEntryPoint}가 401.
 *
 * <p>GitHub OAuth2는 {@code /oauth2/authorization/github}에서 시작 → GitHub → {@code /login/oauth2/code/github}
 * 콜백 → {@link OAuth2SuccessHandler}가 User upsert, JWT 발급 후 프론트로 fragment redirect.
 *
 * <p>Session policy는 {@link SessionCreationPolicy#IF_REQUIRED}로 둔다. OAuth2 authorization_request를
 * 세션에 저장해야 state CSRF 방어가 성립하기 때문이다. JWT 필터는 요청마다 SecurityContext를 새로
 * 덮어쓰므로 API 보호에는 영향이 없다 (stateless JWT 인증 흐름 자체는 세션을 만들지 않음).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final OAuth2SuccessHandler oauth2SuccessHandler;
    private final OAuth2FailureHandler oauth2FailureHandler;
    private final GithubOAuth2UserService githubOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(o -> o
                .userInfoEndpoint(u -> u.userService(githubOAuth2UserService))
                .successHandler(oauth2SuccessHandler)
                .failureHandler(oauth2FailureHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

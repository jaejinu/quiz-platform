package com.quiz.auth;

import com.quiz.domain.user.OAuthProvider;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import com.quiz.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발 편의용 HOST 시드. 프론트에서 로그인만으로 방 생성 테스트가 가능하도록.
 *
 * <p>이미 존재하면 로그만 찍고 재생성하지 않는다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class HostSeedRunner implements ApplicationRunner {

    private static final String HOST_EMAIL = "host@local.dev";
    private static final String HOST_PASSWORD = "hostpass123";
    private static final String HOST_NICKNAME = "local-host";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(HOST_EMAIL)) {
            log.info("[seed] HOST already exists: email={}", HOST_EMAIL);
            return;
        }
        User host = User.builder()
            .email(HOST_EMAIL)
            .nickname(HOST_NICKNAME)
            .passwordHash(passwordEncoder.encode(HOST_PASSWORD))
            .role(UserRole.HOST)
            .oauthProvider(OAuthProvider.LOCAL)
            .build();
        userRepository.save(host);
        log.info("[seed] HOST created: email={} password={} (local profile only)",
            HOST_EMAIL, HOST_PASSWORD);
    }
}

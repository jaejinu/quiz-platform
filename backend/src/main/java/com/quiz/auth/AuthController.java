package com.quiz.auth;

import com.quiz.auth.dto.LoginRequest;
import com.quiz.auth.dto.RefreshRequest;
import com.quiz.auth.dto.SignupRequest;
import com.quiz.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest req) {
        log.debug("POST /api/auth/signup email={}", req.email());
        TokenResponse token = authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        log.debug("POST /api/auth/login email={}", req.email());
        TokenResponse token = authService.login(req);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        log.debug("POST /api/auth/refresh");
        TokenResponse token = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        log.debug("POST /api/auth/logout");
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

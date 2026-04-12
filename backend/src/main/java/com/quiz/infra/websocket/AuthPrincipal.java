package com.quiz.infra.websocket;

import java.security.Principal;

public record AuthPrincipal(Long userId, String nickname, String role) implements Principal {

    @Override
    public String getName() {
        return "user:" + userId;
    }
}

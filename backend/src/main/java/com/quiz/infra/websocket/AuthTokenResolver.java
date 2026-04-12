package com.quiz.infra.websocket;

import java.util.Optional;

public interface AuthTokenResolver {

    Optional<AuthPrincipal> resolve(String authorizationHeader);
}

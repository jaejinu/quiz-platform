package com.quiz.infra.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "quiz.websocket")
public record WebSocketProperties(List<String> allowedOrigins, String endpoint) {

    public WebSocketProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        endpoint = (endpoint == null || endpoint.isBlank()) ? "/ws" : endpoint;
    }
}

package com.quiz.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * 현재 STOMP 연결 유저 수 게이지. SimpUserRegistry는 Spring WebSocket이 제공.
 */
@Component
@RequiredArgsConstructor
public class WebSocketMetrics {

    private final MeterRegistry registry;
    private final SimpUserRegistry simpUserRegistry;

    @PostConstruct
    void register() {
        Gauge.builder("quiz.websocket.sessions", simpUserRegistry, SimpUserRegistry::getUserCount)
                .description("Number of connected STOMP users")
                .register(registry);
    }
}

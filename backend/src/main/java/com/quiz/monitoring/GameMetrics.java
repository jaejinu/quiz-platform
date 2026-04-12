package com.quiz.monitoring;

import com.quiz.domain.game.GameStateStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 활성 방 수 게이지. scrape 시점에 GameStateStore.getActiveRoomIds().size() 호출.
 */
@Component
@RequiredArgsConstructor
public class GameMetrics {

    private final MeterRegistry registry;
    private final GameStateStore gameStateStore;

    @PostConstruct
    void register() {
        Gauge.builder("quiz.active.rooms", gameStateStore, s -> s.getActiveRoomIds().size())
                .description("Number of active quiz rooms currently in play")
                .register(registry);
    }
}

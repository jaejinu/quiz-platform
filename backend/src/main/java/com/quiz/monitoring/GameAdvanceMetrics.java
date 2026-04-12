package com.quiz.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * GameService.advance 호출 지연 메트릭.
 * lock 실패로 조기 return 하는 경로도 기록됨 — "빠른 return" 자체가 경쟁 상황 시그널.
 */
@Component
public class GameAdvanceMetrics {

    private final Timer advanceTimer;

    public GameAdvanceMetrics(MeterRegistry registry) {
        this.advanceTimer = Timer.builder("quiz.game.advance.duration")
                .description("Latency of GameService.advance including lock contention fast-returns")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Timer advanceTimer() {
        return advanceTimer;
    }
}

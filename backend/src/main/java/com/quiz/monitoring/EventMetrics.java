package com.quiz.monitoring;

import com.quiz.infra.redis.RoomEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Redis Pub/Sub 이벤트 파이프라인 메트릭.
 * - quiz.events.published {type}
 * - quiz.events.received  {type}
 * - quiz.redis.pubsub.latency (Timer, percentile histogram)
 *
 * type은 RoomEventType enum이므로 고카디널리티 위험 없음(현재 7개).
 */
@Component
public class EventMetrics {

    private final MeterRegistry registry;
    private final Map<RoomEventType, Counter> published = new EnumMap<>(RoomEventType.class);
    private final Map<RoomEventType, Counter> received = new EnumMap<>(RoomEventType.class);
    private Timer pubsubLatencyTimer;

    public EventMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void register() {
        this.pubsubLatencyTimer = Timer.builder("quiz.redis.pubsub.latency")
                .description("Duration between publish timestamp and subscriber receive time")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void incrementPublished(RoomEventType type) {
        published.computeIfAbsent(type, t ->
                Counter.builder("quiz.events.published")
                        .tag("type", t.name())
                        .description("Number of room events published to Redis")
                        .register(registry)
        ).increment();
    }

    public void incrementReceived(RoomEventType type) {
        received.computeIfAbsent(type, t ->
                Counter.builder("quiz.events.received")
                        .tag("type", t.name())
                        .description("Number of room events received from Redis (after self-skip)")
                        .register(registry)
        ).increment();
    }

    public void recordPubSubLatency(Duration d) {
        if (d == null) {
            return;
        }
        // 음수(시계 skew)는 0으로 기록
        if (d.isNegative()) {
            d = Duration.ZERO;
        }
        pubsubLatencyTimer.record(d);
    }
}

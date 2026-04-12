package com.quiz.monitoring;

import com.quiz.infra.rabbitmq.RabbitConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 답변 큐 깊이 게이지.
 * 실패 시 -1 반환 — Prometheus 쪽에서 음수로 이상 감지 가능.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMetrics {

    private final MeterRegistry registry;
    private final RabbitAdmin rabbitAdmin;

    @PostConstruct
    void register() {
        Gauge.builder("quiz.rabbitmq.answers.queue.depth", this, RabbitMetrics::readDepth)
                .tag("queue", RabbitConfig.QUEUE_ANSWERS)
                .description("Current message count in the answers queue")
                .register(registry);
    }

    private double readDepth() {
        try {
            QueueInformation info = rabbitAdmin.getQueueInfo(RabbitConfig.QUEUE_ANSWERS);
            if (info == null) {
                return -1d;
            }
            return info.getMessageCount();
        } catch (Exception e) {
            log.warn("failed to read rabbitmq queue depth queue={}: {}",
                    RabbitConfig.QUEUE_ANSWERS, e.getMessage());
            return -1d;
        }
    }
}

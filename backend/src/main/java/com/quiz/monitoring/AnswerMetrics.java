package com.quiz.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 답변 파이프라인 메트릭.
 * - quiz.answers.enqueued (Counter)       — AnswerService에서 RabbitMQ로 enqueue 성공 시
 * - quiz.answers.processed {outcome}      — AnswerProcessor 결과 (ok/duplicate/error)
 * - quiz.answer.processing.duration (Timer, percentile histogram) — AnswerProcessor 처리 시간
 */
@Component
public class AnswerMetrics {

    private final Counter enqueued;
    private final Map<String, Counter> processedByOutcome;
    private final Timer processingTimer;

    public AnswerMetrics(MeterRegistry registry) {
        this.enqueued = Counter.builder("quiz.answers.enqueued")
                .description("Answers enqueued to RabbitMQ for async processing")
                .register(registry);

        this.processedByOutcome = Map.of(
                "ok", Counter.builder("quiz.answers.processed")
                        .tag("outcome", "ok")
                        .description("Answers processed successfully")
                        .register(registry),
                "duplicate", Counter.builder("quiz.answers.processed")
                        .tag("outcome", "duplicate")
                        .description("Answers skipped as duplicate")
                        .register(registry),
                "error", Counter.builder("quiz.answers.processed")
                        .tag("outcome", "error")
                        .description("Answers that failed processing and will route to DLQ")
                        .register(registry)
        );

        this.processingTimer = Timer.builder("quiz.answer.processing.duration")
                .description("Latency of AnswerProcessor.onMessage")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Counter counterEnqueued() {
        return enqueued;
    }

    public Counter counterProcessed(String outcome) {
        Counter c = processedByOutcome.get(outcome);
        if (c == null) {
            throw new IllegalArgumentException("unknown outcome: " + outcome);
        }
        return c;
    }

    public Timer processingTimer() {
        return processingTimer;
    }
}

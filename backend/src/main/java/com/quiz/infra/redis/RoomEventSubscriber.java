package com.quiz.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.infra.tracing.TraceContextSupport;
import com.quiz.monitoring.EventMetrics;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventSubscriber implements MessageListener {

    private static final String TOPIC_PREFIX = "/topic/room/";
    private static final String INSTRUMENTATION_SCOPE = "com.quiz.redis.pubsub";

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("publisherId")
    private final String publisherId;
    private final EventMetrics eventMetrics;
    private final TraceContextSupport traceContextSupport;
    private final OpenTelemetry openTelemetry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        RoomEvent event;
        try {
            event = objectMapper.readValue(body, RoomEvent.class);
        } catch (Exception e) {
            log.error("failed to deserialize RoomEvent, dropping message: body={} err={}", body, e.getMessage(), e);
            return;
        }

        if (publisherId.equals(event.publisherId())) {
            log.trace("self-skip roomId={} type={}", event.roomId(), event.type());
            return;
        }

        // 원격 서버에서 온 이벤트 — traceparent로 parent context 복원 후 consumer span 생성
        Context parent = traceContextSupport.extract(event.traceparent());
        Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        Span span = tracer.spanBuilder("redis.pubsub.receive")
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("messaging.system", "redis")
                .setAttribute("messaging.destination", "room." + event.roomId())
                .setAttribute("messaging.operation", "receive")
                .setAttribute("event.type", event.type().name())
                .setAttribute("room.id", event.roomId())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            eventMetrics.incrementReceived(event.type());
            if (event.timestamp() != null) {
                eventMetrics.recordPubSubLatency(Duration.between(event.timestamp(), Instant.now()));
            }
            simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + event.roomId(), event);
        } catch (RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}

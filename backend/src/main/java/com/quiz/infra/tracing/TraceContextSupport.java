package com.quiz.infra.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * W3C Trace Context 헤더({@code traceparent})를 Redis Pub/Sub 경계에서 주입/추출하는 헬퍼.
 *
 * <p>Spring Boot 자동 구성이 {@link OpenTelemetry} 빈을 제공한다
 * ({@code micrometer-tracing-bridge-otel} + OTLP exporter 조합). 해당 빈의 propagator를
 * 이용해 현재 span을 문자열로 직렬화하거나, 원격에서 받은 문자열로 parent {@link Context}를
 * 복원한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceContextSupport {

    private static final String TRACEPARENT = "traceparent";

    private final OpenTelemetry openTelemetry;

    /**
     * 현재 Otel {@link Context}로부터 {@code traceparent} 헤더 문자열을 추출한다.
     * active span이 없거나 sampling 제외되면 null 반환.
     */
    public String currentTraceparent() {
        Map<String, String> carrier = new HashMap<>();
        openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * 원격에서 건너온 {@code traceparent} 문자열을 parent {@link Context}로 복원.
     * null/blank면 {@link Context#root()} 반환 (루트 span이 새로 만들어짐).
     */
    public Context extract(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Context.root();
        }
        Map<String, String> carrier = Map.of(TRACEPARENT, traceparent);
        return openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), carrier, MapGetter.INSTANCE);
    }

    private static final class MapGetter implements TextMapGetter<Map<String, String>> {
        private static final MapGetter INSTANCE = new MapGetter();

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null) {
                return null;
            }
            return carrier.get(key);
        }
    }
}

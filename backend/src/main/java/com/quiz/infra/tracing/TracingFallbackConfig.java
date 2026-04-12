package com.quiz.infra.tracing;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing 비활성({@code management.tracing.enabled=false}) 환경 — e.g. Step 6 통합 테스트 —
 * 에서도 {@link OpenTelemetry} 빈이 존재하도록 보장하는 안전망.
 *
 * <p>Spring Boot 의 {@code OpenTelemetryAutoConfiguration} 이 빈을 제공하면 여기서는 아무것도
 * 등록하지 않고, 제공하지 않을 때만 {@link OpenTelemetry#noop()} 를 대신 노출한다. 이렇게 하면
 * {@code TraceContextSupport}, {@code GameStompController}, {@code RoomEventSubscriber},
 * {@code GameService} 등의 생성자 주입이 tracing 유무와 무관하게 성공한다.</p>
 */
@Configuration(proxyBeanMethods = false)
public class TracingFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry noopOpenTelemetry() {
        return OpenTelemetry.noop();
    }
}

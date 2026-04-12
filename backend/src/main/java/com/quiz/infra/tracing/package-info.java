/**
 * OpenTelemetry context propagation 유틸 — Redis Pub/Sub 경계에서 W3C
 * {@code traceparent} 헤더를 주입/추출하여 publisher → remote subscriber → STOMP fan-out
 * 을 하나의 trace로 연결한다.
 */
package com.quiz.infra.tracing;

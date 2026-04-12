# 분산 트레이싱 (Step 10)

## 개요

Spring Boot 3.3 + Micrometer Tracing + OpenTelemetry Bridge로 trace 수집, Grafana Tempo로 저장/조회.

## 아키텍처

```
Spring Boot 앱 (Micrometer Tracing + OTel Bridge)
       │
       │ OTLP HTTP :4318/v1/traces
       ▼
Grafana Tempo (traces 저장, 로컬 24h retention)
       │
       │ :3200 (query API)
       ▼
Grafana UI (http://localhost:3001)
```

## 자동 instrumentation

| 컴포넌트 | 자동 | 비고 |
|---|---|---|
| Spring MVC (`/api/**`) | ✓ | |
| JDBC/JPA | ✓ | Micrometer Observation |
| RabbitMQ publish/consume | ✓ | `spring.rabbitmq.{template,listener.simple}.observation-enabled=true` 필수 |
| Redis Lettuce 일반 명령 | ✓ | GET/SET 등 |
| Redis Pub/Sub | ✗ | 수동 — `RoomEvent.traceparent` 필드로 전파 |
| STOMP `@MessageMapping` | ✗ | 수동 — `GameStompController`에서 span 시작 |

## 실행

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.monitor.yml up -d
```

- Tempo:      http://localhost:3200 (API)
- Grafana:    http://localhost:3001 (admin/admin) → "Explore" → Tempo 선택

## 샘플 trace 조회

1. Grafana → "Explore" → 데이터소스 **Tempo**
2. Query type: "Search"
3. Service name: `quiz-platform`
4. 최근 trace 목록 → 클릭 → waterfall 뷰

## Exemplars (메트릭 → trace 이동)

1. Grafana Dashboard "Quiz Platform Overview" 오픈
2. Answer processing latency 패널에서 점(exemplar) 클릭
3. 해당 trace로 점프

## 샘플링

- `local`: 100% (`TRACING_SAMPLING=1.0` 기본값)
- `prod`: `TRACING_SAMPLING=0.1` (환경변수로 override)

## 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| 앱 기동 시 OTLP 연결 실패 경고 | Tempo 미기동 | `docker compose -f docker-compose.yml -f docker-compose.monitor.yml up -d tempo` |
| STOMP trace가 answer 큐로 이어지지 않음 | RabbitMQ observation 비활성 | `spring.rabbitmq.listener.simple.observation-enabled: true` 확인 |
| Redis Pub/Sub trace 끊김 | RoomEvent.traceparent 필드 누락 | `RoomEventPublisher`가 `TraceContextSupport.currentTraceparent()` 호출하는지 확인 |
| Grafana에서 Tempo 데이터소스 안 보임 | provisioning 마운트 실패 | `docker logs quiz-grafana` 로그 확인 |

## 의도적으로 안 한 것

- STOMP 프레임 헤더 traceparent 전파 (클라→서버 trace 이어지기) — 실무 니즈 낮음, 범위 외
- Loki 로그 연동 (trace-to-logs) — 로깅 스택 확장 시 별도 Step
- Alertmanager 연동 — 메트릭 알림은 Step 5에 있지만 traces alerting은 범위 외
- Tempo metrics_generator — service graph는 Grafana의 "Service Graph" 기능으로 대체 가능하나 이번 구성에선 비활성

## 라이선스

- Grafana Tempo: AGPL 3.0 (배포 산출물 X, 컨테이너 이미지 사용만 하므로 영향 없음)
- OpenTelemetry Java: Apache 2.0
- Micrometer: Apache 2.0

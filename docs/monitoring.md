# Monitoring Guide (Step 5-B)

Prometheus + Grafana 기반 모니터링 스택. 로컬 개발 환경에서 Quiz Platform의 핵심 지표와 알림을 확인한다.

## 실행

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.monitor.yml up -d
```

접속:
- Grafana: http://localhost:3001 (admin / admin)
- Prometheus: http://localhost:9090
- Prometheus Alerts UI: http://localhost:9090/alerts

## 메트릭 카탈로그

백엔드(`host.docker.internal:8080/actuator/prometheus`)가 노출하는 커스텀 지표 요약.

| 메트릭 | 타입 | 설명 |
| --- | --- | --- |
| `quiz_active_rooms` | gauge | 현재 활성 퀴즈 방 수 |
| `quiz_websocket_sessions` | gauge | 활성 WebSocket 세션 수 |
| `quiz_answer_processing_duration_seconds` | histogram | 답안 처리 지연 (P50/P95/P99) |
| `quiz_events_published_total{type}` | counter | Pub/Sub로 발행된 이벤트 수 (타입별) |
| `quiz_events_received_total{type}` | counter | Pub/Sub로 수신된 이벤트 수 (타입별) |
| `quiz_rabbitmq_answers_queue_depth` | gauge | RabbitMQ `quiz.answers` 큐 길이 |
| `quiz_redis_pubsub_latency_seconds` | histogram | Redis Pub/Sub 왕복 지연 |
| `quiz_answers_processed_total{outcome}` | counter | 답안 처리 결과 카운트 (success/error) |

## Grafana 대시보드

- UID: `quiz-overview`, 제목: `Quiz Platform Overview`
- 새로고침 주기: 10s
- 기본 8개 패널 (2열 grid):
  1. Active Rooms (stat)
  2. WebSocket Sessions
  3. Answer Processing Latency (P50/P95/P99)
  4. Events Published by Type (stacked)
  5. Events Received by Type (stacked)
  6. RabbitMQ Queue Depth
  7. Pub/Sub Latency P95
  8. Answer Error Rate

### 대시보드 리로드

Grafana provisioning은 hot-reload를 지원한다. `docker/grafana/dashboards/quiz-overview.json`을 수정하고 저장하면 몇 초 내 Grafana UI에 반영된다. 강제 반영이 필요하면:

```bash
docker restart quiz-grafana
```

데이터소스(`docker/grafana/provisioning/datasources/prometheus.yml`)를 바꿨을 때는 Grafana 컨테이너 재시작 필요.

## Alerts

`docker/prometheus/alerts.yml`에 정의된 4개 규칙:

| Alert | 조건 | severity |
| --- | --- | --- |
| `HighAnswerLatency` | answer P95 > 500ms (3m) | warning |
| `RabbitQueueBacklog` | queue depth > 500 (2m) | warning |
| `AnswerErrorRate` | error rate > 5% (5m) | critical |
| `PubSubLatencyHigh` | pub/sub P95 > 200ms (3m) | warning |

현재 Step에서는 Alertmanager를 설치하지 않았다. 알림 발화 여부는 Prometheus UI `/alerts`에서만 확인한다. 규칙 파일을 수정한 경우 Prometheus 컨테이너 재시작 또는 reload:

```bash
docker restart quiz-prometheus
# 또는
curl -X POST http://localhost:9090/-/reload   # 런타임 reload 옵션 활성화 시
```

## 디렉토리 구조

```
docker/
  docker-compose.monitor.yml
  prometheus/
    prometheus.yml
    alerts.yml
  grafana/
    provisioning/
      datasources/prometheus.yml
      dashboards/dashboards.yml
    dashboards/
      quiz-overview.json
```

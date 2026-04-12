# Load Test Result

## 환경
- 실행 일시: TBD
- 하드웨어: TBD
- 서버 인스턴스 수: TBD

## 설정
- Stages: 50 -> 500 -> 1000 VU, 총 17분
- 방 10개, 각 5퀴즈

## 결과 지표

### k6
| 지표 | 값 |
|---|---|
| WebSocket CONNECT 성공률 | TBD |
| 평균 VU | TBD |
| checks pass rate | TBD |
| 총 WebSocket 메시지 | TBD |

### Prometheus (Grafana 스크린샷 경로)
| 메트릭 | P50 | P95 | P99 |
|---|---|---|---|
| quiz.answer.processing.duration | | | |
| quiz.redis.pubsub.latency | | | |

### 병목 지점
- (실행 후 분석)

### 개선 제안
- (실행 후 분석)

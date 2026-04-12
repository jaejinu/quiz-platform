# Load Tests

## 사전 준비
1. 백엔드 + 인프라 실행:
   ```bash
   cd docker && docker compose up -d
   cd ../backend && ./gradlew bootRun --args='--spring.profiles.active=local'
   ```
2. k6 설치: `choco install k6` (Windows) / `brew install k6` (Mac) / https://k6.io/docs/get-started/installation/

## 실행
```bash
cd loadtest/k6
k6 run game-load.js
```

환경변수:
- `BASE_URL` (기본 `ws://localhost:8080/ws/websocket`)
- `HTTP_BASE_URL` (기본 `http://localhost:8080`)

예:
```bash
BASE_URL=ws://staging.example.com/ws/websocket \
HTTP_BASE_URL=http://staging.example.com \
k6 run game-load.js
```

## 결과 해석
- `checks` pass rate 95% 이상
- `ws_connecting{status:success}` 99% 이상
- 백엔드 Grafana 대시보드(http://localhost:3001)에서:
  - `quiz_answer_processing_duration` P95/P99
  - `quiz_rabbitmq_answers_queue_depth` 백로그
  - `quiz_websocket_sessions` 실시간 접속자

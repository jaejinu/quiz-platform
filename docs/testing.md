# Testing Guide

## 통합 테스트 (Testcontainers)

### 사전 조건
- Docker Desktop 실행 중
- (선택) `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` — 컨테이너 재사용으로 2회차부터 빨라짐

### 실행
```bash
cd backend
./gradlew test --tests 'com.quiz.integration.*'
```

### 시나리오
1. `GameLifecycleIntegrationTest` — 방/퀴즈/답변/리더보드 전체 사이클
2. `DistributedAdvanceLockTest` — 동시 advance 호출 시 Redis lock 단일 점유 (한계: 단일 JVM)
3. `ReconnectRecoveryTest` — 30초 grace + snapshot
4. `DuplicateAnswerTest` — DB unique + duplicate counter
5. `RabbitDlqTest` — 존재하지 않는 quizId -> DLQ
6. `PubSubSelfSkipTest` — 자기 발행 메시지 중복 수신 방지
7. `TimerExpirationTest` — 무답변 퀴즈 타이머 만료 -> Watchdog advance

### 한계
- 분산 advance 테스트는 단일 JVM에서 `GameService` 인스턴스 2개 수동 생성. 실제 다중 pod 환경과 동일하지 않음.
- k6 부하 테스트는 SockJS 경로 대신 raw WebSocket 사용 — 실제 프론트와 약간 다른 경로.

## 부하 테스트
`loadtest/README.md` 참조.

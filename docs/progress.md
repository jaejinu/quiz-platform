# 진행 로그

날짜 형식: `YYYY-MM-DD`. 각 단계 완료 시 간단히 기록.

---

## 2026-04-12 — Step 1: 프로젝트 초기화

### 완료 항목
- 루트 구조 확립: `backend/`, `frontend/`, `docker/`, `docs/`, `.github/workflows/`
- **백엔드** (Spring Boot 3.3, Java 21, Gradle)
  - `build.gradle`: Web, WebSocket, Security, JPA, Redis, AMQP, Actuator, Prometheus, Lombok, Testcontainers
  - 메인 애플리케이션 클래스 + 프로파일 분리(`application.yml` / `application-local.yml`)
  - 도메인형 패키지 스켈레톤: `domain/{room,quiz,participant,answer,leaderboard}`, `infra/{redis,rabbitmq,websocket}`, `api`, `config`, `monitoring`
- **인프라** (`docker/docker-compose.yml`): PostgreSQL 16, Redis 7, RabbitMQ 3 management
- **프론트엔드**: Vite + React 18 + STOMP 클라이언트 최소 스캐폴딩
- **문서**: `CLAUDE.md`, `README.md`, `docs/architecture.md`, `docs/progress.md`

### 다음 단계
Step 2 — 도메인 엔티티 생성 (User, QuizRoom, Quiz, Participant, Answer) + JPA Repository.

### 메모
- 최초 1회 gradle wrapper 생성 필요 (`gradle wrapper --gradle-version 8.10` 또는 IntelliJ 자동 생성)
- 프론트 STOMP 연결은 Step 3에서 실제 WebSocket 엔드포인트 붙으면 테스트 가능

---

## 2026-04-12 — Step 2: 도메인 엔티티 + JPA

### 완료 항목
- **JPA Auditing** (`config/JpaConfig.java`, `domain/common/BaseTimeEntity.java`) — `createdAt`/`updatedAt` 자동 관리
- **User 도메인**: `User`, `UserRole(HOST|PLAYER)`, `OAuthProvider(LOCAL|GITHUB)`, `UserRepository`
- **Room 도메인**: `QuizRoom`(code 6자리, 상태 전이 start/finish 메서드), `RoomStatus`, `QuizRoomRepository`
- **Quiz 도메인**: `Quiz`(options는 `@JdbcTypeCode(SqlTypes.JSON)`으로 PostgreSQL JSONB 저장), `QuizType(SINGLE|MULTIPLE|OX|SHORT)`, `isCorrect()` 정답 판정 메서드
- **Participant 도메인**: `Participant`(sessionId 재접속 복구 지원, reconnect/disconnect/leave), `ParticipantStatus`
- **Answer 도메인**: `Answer`(참가자-퀴즈 유니크), `ScoreCalculator`(최대 1000점, 응답시간 기반)
- **Leaderboard 키 규약**: `LeaderboardKeys`(Redis 키/채널 네이밍 중앙 집중)

### 설계 결정
- **@ManyToOne 관계 미사용**: 각 도메인이 ID 참조로만 연결 → 도메인 간 결합도 낮추고 분산 설계 용이
- **상태 전이는 도메인 메서드로**: `QuizRoom.start()`/`finish()` 내부에서 검증 → 서비스 레이어에서 직접 필드 변경 금지
- **JSON 컬럼**: Hibernate 6의 `@JdbcTypeCode(SqlTypes.JSON)` 사용 (별도 컨버터 불필요)
- **Builder 패턴**: `@Builder(access = private)` + `protected` 기본 생성자로 JPA 호환 + 외부 생성 차단

### 다음 단계
Step 3 — WebSocket(STOMP) + Redis Pub/Sub 분산 브리지 설정.

---

## 2026-04-12 — Step 3: WebSocket(STOMP) + Redis Pub/Sub 이중 경로 브리지

**진행 방식**: Plan 에이전트로 설계서 선행 → 사용자 결정(B: 이중 경로) → 구현 에이전트 3개(A/B/C) 병렬 실행 → 메인에서 통합 보정.

### 완료 항목

#### (A) WebSocket 영역 — `com.quiz.infra.websocket`
- `WebSocketProperties` (record, `@ConfigurationProperties("quiz.websocket")`, null-safe 초기화)
- `AuthPrincipal` (record implements `java.security.Principal`, `getName() = "user:" + userId`)
- `AuthTokenResolver` 인터페이스 — Step 4에서 JWT 구현체로 교체될 경계
- `StubAuthTokenResolver` (`@ConditionalOnMissingBean(AuthTokenResolver.class)`) — 토큰 포맷 `Bearer stub:<userId>:<nickname>:<role>`
- `StompAuthChannelInterceptor` — CONNECT 프레임에서 Authorization 파싱, 실패 시 `MessageDeliveryException("Unauthorized")`
- `WebSocketConfig` — `/ws` SockJS, SimpleBroker(`/topic`, `/queue`), app prefix `/app`, user prefix `/user`

#### (B) Redis Pub/Sub 브리지 — `com.quiz.infra.redis`
- `RoomEventType` enum (PLAYER_JOINED, PLAYER_LEFT, GAME_STARTED, QUIZ_PUSHED, ANSWER_SUBMITTED, LEADERBOARD_UPDATED, GAME_FINISHED)
- `RoomEvent` (record, `@JsonCreator`/`@JsonIgnoreProperties(ignoreUnknown=true)`)
- `RoomEventPublisher` — **이중 경로 발행**:
  1. 로컬: `simpMessagingTemplate.convertAndSend("/topic/room/{roomId}", event)`
  2. 원격: `redisTemplate.convertAndSend("quiz:room:{roomId}", event)`
- `RoomEventSubscriber` — **self-skip**: `event.publisherId().equals(self)`이면 early return (로컬 중복 전송 방지)
- `RedisConfig` — `StringRedisTemplate` + `@Primary RedisTemplate<String,Object>` (GenericJackson2JsonRedisSerializer) + `RedisMessageListenerContainer`(패턴 `quiz:room:*`, ThreadPool 4/8)

#### (C) Common Config — `com.quiz.config`
- `JacksonConfig` — `@Primary ObjectMapper` (JavaTimeModule + `WRITE_DATES_AS_TIMESTAMPS=false` + `FAIL_ON_UNKNOWN_PROPERTIES=false`)
- `InstanceIdConfig` — `@Bean publisherId` (UUID 랜덤, `QUIZ_INSTANCE_ID`로 override 가능)

#### (통합 보정 — 메인 세션)
- **`backend/lombok.config`** — `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier`
  → `@RequiredArgsConstructor`가 생성하는 생성자 파라미터에 `@Qualifier` 전파되도록. 이게 없으면 `publisherId` 주입이 런타임에 실패.
- **`SecurityConfig`** — `spring-boot-starter-security`의 기본 basic auth 비활성화(WebSocket 핸드셰이크/REST 경로 `permitAll`). Step 4에서 JWT 필터 추가.
- **`frontend/src/App.jsx`** — `connectHeaders.Authorization: "Bearer stub:<userId>:<nickname>:<role>"` 추가, UI에 userId/nickname/role 입력 필드 추가

### 이중 경로 작동 시퀀스
```
Server1                    Redis           Server2
 Publisher.publish()
   ├─ local simpMsg ──►로컬 유저 A
   └─ redis publish ──► "quiz:room:42" ───► Subscriber.onMessage
                                             publisherId == self? NO
                                             simpMsg ──► 원격 유저 B
 Server1도 자기 발행을 수신:
                            ◄──Subscriber.onMessage
                               publisherId == self? YES → skip (중복 방지)
```

### 검증 대상 (Step 4 이전)
- Testcontainers로 Redis 띄우고 publisherId 다른 두 Publisher가 서로의 메시지를 받는지 (Step 6에서 진행)
- 로컬 수동: `QUIZ_INSTANCE_ID=s1 ./gradlew bootRun --args='--server.port=8080'` / `QUIZ_INSTANCE_ID=s2 ./gradlew bootRun --args='--server.port=8081'` + 프론트 2개

### 다음 단계
Step 4 — 핵심 게임 로직 (방 생성/입장/시작/답변 처리/리더보드). `@MessageMapping` 컨트롤러 + 서비스 계층 + RabbitMQ 답변 큐 + Redis Sorted Set.

---

## 2026-04-12 — Step 4: 핵심 게임 로직

**진행 방식**: Plan 에이전트로 설계서 선행 → 사용자 결정(A: 설계 그대로 + 4개 병렬) → 메인이 사전 계약 2개 세팅 → 구현 에이전트 4개(A/B/C/D) 병렬 실행 → 메인 통합 보정.

### 사전 계약 (메인 직접)
- `RoomEventPublisher.publishAfterCommit(roomId, type, payload)` — 활성 트랜잭션이면 `afterCommit`에 예약, 아니면 즉시 발행. DB save와 이벤트 원자성 확보.
- `LeaderboardEntry(Long userId, String nickname, int score)` — 공유 record

### 완료 항목

#### (A) REST + 방/퀴즈 도메인 — 총 ~18개 파일
- 예외 계층: `QuizException`, `RoomNotFoundException`, `QuizNotFoundException`, `UnauthorizedException`, `InvalidGameStateException`, `DuplicateAnswerException`, `RoomCodeGenerationFailedException`
- `ErrorResponse`, `GlobalExceptionHandler` (`@RestControllerAdvice` — code별 HTTP 상태 분기)
- `WebMvcConfig` — CORS (아래 메인 보정으로 `CorsConfigurationSource` 빈 방식으로 교체)
- DTO: `CreateRoomRequest`(hostId 임시), `RoomResponse`, `CreateQuizRequest`, `QuizResponse`(정답 마스킹 지원), `QuizListResponse`
- `RoomCodeGenerator` — SecureRandom 6자리 영숫자(0,O,1,I 제외), `existsByCode` 충돌 시 최대 5회 재시도
- `RoomService`, `QuizService` + `RoomController`, `QuizAdminController`
- **임시 필드**: `CreateRoomRequest.hostId`, `QuizAdminController.@RequestParam hostId` — Step 5 JWT 도입 시 Principal로 치환

#### (B) WebSocket 컨트롤러 + 참가자/재접속 — 7개 파일
- `SchedulingConfig` (`@EnableScheduling`) — C의 `GameWatchdog`도 함께 활용
- `AnswerSubmission` (record)
- `GameSnapshot` (record) — B가 DTO 정의
- `ParticipantService` — join/leave/disconnect/cleanupExpired(`@Scheduled 1s`), `getByRoomAndUserOrThrow`
  - join 끝에서 `GameSnapshotService.sendSnapshot` 호출로 재접속 복구 일관 처리
  - 30초 grace TTL: `pending_cleanups` ZSet에 deadline 저장, 1초 주기 polling
- `GameSnapshotService` — `GameStateStore.snapshot` + `LeaderboardService.top(10)` + `QuizService.getForPlayer` 조립 → `/user/queue/snapshot`
- `SessionRegistryListener` (`@EventListener SessionDisconnectEvent`)
- `GameStompController` — `@Controller` + `@MessageMapping` (join/start/answer/leave) + `@MessageExceptionHandler` → `@SendToUser("/queue/errors")`

#### (C) 게임 엔진 — 8개 파일
- `GameStateKeys` (Redis 키 중앙집중: currentQuizId/Index/startedAt/deadline/advanceLock/answerCount/ACTIVE_ROOMS)
- `GameStateSnapshot` (record)
- `GameStateStore` — StringRedisTemplate 기반 상태 read/write, `tryAcquireAdvanceLock(SET NX PX 5s)`, `incrementAnswerCount(INCR + TTL 1h)`, `clear(DEL 상수 키 목록)`
- `LeaderboardService` — `ZINCRBY`, `ZREVRANGE WITHSCORES` + `UserRepository.findAllById` 배치 nickname 매핑, `finalizeAndExpire`, `clear`
- `QuizTimerScheduler` — 빈 `ScheduledExecutorService gameScheduler`(8 threads) 주입, 로컬 스케줄(@PreDestroy shutdown)
- `GameWatchdog` — `@Scheduled(fixedRate=1000)` — 활성 방 스캔 후 deadline 초과 시 `GameService.advance` 호출 (로컬 타이머 유실/프로세스 재시작 안전망)
- `GameService` — `start`/`advance`/`finish`/`onAnswerCounted` (모두 `@Transactional`, `publishAfterCommit` 사용)
- `GameConfig` — `ScheduledExecutorService gameScheduler` 빈 (`@Bean(destroyMethod="")`)

#### (D) RabbitMQ 답변 파이프라인 — 4개 파일
- `RabbitConfig` — Exchange/Queue/DLQ + Binding + `Jackson2JsonMessageConverter(@Primary ObjectMapper)` + `SimpleRabbitListenerContainerFactory`(concurrency 4/16, prefetch 20, requeueRejected=false)
- `AnswerQueueMessage` (record, `@JsonCreator`) — `(roomId, participantId, userId, nickname, quizId, answer, responseTimeMs, submittedAt, publisherId)`
- `AnswerService` — stale answer drop + participant lookup + responseTime 계산 + enqueue
- `AnswerProcessor` — `@RabbitListener + @Transactional`:
  1. 중복 체크 → skip
  2. `Quiz.isCorrect` 판정 → `ScoreCalculator.calculate`
  3. `Answer` save (unique 위반 catch)
  4. `leaderboardService.incrementScore`
  5. `gameStateStore.incrementAnswerCount` → `publishAfterCommit(ANSWER_SUBMITTED)`
  6. `leaderboardService.top(10)` → `publishAfterCommit(LEADERBOARD_UPDATED)`
  7. `gameService.onAnswerCounted` (전원 제출 시 조기 advance)

### 통합 보정 (메인 세션 — 4개 병렬의 한계)

병렬 4개는 Step 3(3개)보다 크로스-커팅 이슈가 더 많이 드러남:

1. **`GameService.onAnswerCounted` self-invocation 위험** — AnswerProcessor 트랜잭션 밖에서 직접 호출될 경우 내부 `advance()`의 `@Transactional`이 프록시 우회로 무시됨. `onAnswerCounted`에 `@Transactional` 명시 추가.
2. **CORS + Spring Security 순서 문제** — A의 `WebMvcConfig.addCorsMappings`는 Security 필터 뒤에서만 적용. preflight(OPTIONS)가 막힐 수 있음. `CorsConfigurationSource` 빈 방식으로 교체 + `SecurityConfig.cors(Customizer.withDefaults())` 활성화.
3. **프론트엔드 contract 확장** — 새 destination 4개(`/join`, `/start`, `/answer`, `/leave`) + 새 subscribe 3개(`/topic/room/{id}`, `/user/queue/snapshot`, `/user/queue/errors`) + REST 2개(`POST /api/rooms`, `POST /api/rooms/{id}/quizzes`) 지원하도록 App.jsx 대폭 갱신.

### 전체 흐름 시퀀스 (요약)

```
HOST: POST /api/rooms {title, maxPlayers, defaultTimeLimit, hostId}
  → RoomService.create → QuizRoom save(WAITING) + code 발급
HOST: POST /api/rooms/{id}/quizzes?hostId=... (반복)
PLAYERS: SockJS CONNECT /ws + Authorization: Bearer stub:...
PLAYERS: SEND /app/room/{id}/join → Participant save + PLAYER_JOINED
HOST: SEND /app/room/{id}/start → GameService.start
  → QuizRoom.start() + GameStateStore.init + GAME_STARTED + QUIZ_PUSHED
  → QuizTimerScheduler.schedule(deadline)
PLAYERS: SEND /app/room/{id}/answer {quizId, answer}
  → AnswerService enqueue → RabbitMQ → AnswerProcessor
    → Quiz.isCorrect + Score + Answer save + ZINCRBY
    → ANSWER_SUBMITTED + LEADERBOARD_UPDATED + GameService.onAnswerCounted
(타이머 만료 OR 전원 제출) → GameService.advance(lock-guard)
  → 다음 퀴즈 QUIZ_PUSHED or finish → GAME_FINISHED
```

### 검증 TODO (Step 5/6에서)
- Testcontainers로 Redis/RabbitMQ/Postgres 띄운 통합 테스트
- 두 서버 인스턴스로 advance lock 경쟁 검증
- RabbitMQ 장애 시뮬레이션 (DLQ 도달 테스트)
- k6 부하: 방당 1000 참가자 × 10 퀴즈

### 다음 단계
Step 5 — Prometheus 커스텀 메트릭 (활성 방 수, WebSocket 연결 수, 답변 처리 지연 히스토그램, RabbitMQ 큐 길이) + Grafana 대시보드. 또한 JWT 인증으로 Step 4 임시 필드(`hostId` 파라미터) 제거 고려.

---

## 2026-04-12 — Step 5: Prometheus 메트릭 + Grafana 대시보드

**진행 방식**: Plan 에이전트 설계서 → 결정 간단해서 확인 스킵 → 에이전트 2개(백엔드 / 인프라) 병렬.

### 완료 항목

#### (A) 백엔드 메트릭
- `com.quiz.monitoring` 6개 클래스:
  - `GameMetrics` — Gauge `quiz.active.rooms` (GameStateStore 참조)
  - `WebSocketMetrics` — Gauge `quiz.websocket.sessions` (SimpUserRegistry)
  - `RabbitMetrics` — Gauge `quiz.rabbitmq.answers.queue.depth` (AmqpAdmin, 예외 시 -1)
  - `EventMetrics` — Counter `quiz.events.published/received`(tag=type) + Timer `quiz.redis.pubsub.latency`
  - `AnswerMetrics` — Counter `quiz.answers.enqueued/processed`(outcome=ok/duplicate/error) + Timer `quiz.answer.processing.duration`
  - `GameAdvanceMetrics` — Timer `quiz.game.advance.duration`
- 기존 파일 5군데 훅:
  - `RoomEventPublisher.publish` → publish 성공 시 counter +1
  - `RoomEventSubscriber.onMessage` → self-skip 통과 시 counter +1 + latency record
  - `AnswerService.accept` → enqueue counter +1
  - `AnswerProcessor.onMessage` → try/catch/finally 재구성, outcome별 counter + Timer.Sample
  - `GameService.advance` → Timer.Sample + finally stop (조기 return도 계측)
- `application.yml`에 percentile/histogram/SLO 설정 추가

#### (B) 모니터링 인프라
- `docker/grafana/provisioning/datasources/prometheus.yml` — Prometheus 데이터소스 자동 등록
- `docker/grafana/provisioning/dashboards/dashboards.yml` — 대시보드 provisioning
- `docker/grafana/dashboards/quiz-overview.json` — 8개 패널 (Active Rooms, WebSocket Sessions, Answer Latency P50/P95/P99, Events Published/Received by Type, Rabbit Queue Depth, Pub/Sub P95, Error Rate)
- `docker/prometheus/alerts.yml` — 4개 alert rule (HighAnswerLatency, RabbitQueueBacklog, AnswerErrorRate, PubSubLatencyHigh)
- `docker/prometheus/prometheus.yml` — `rule_files: ["alerts.yml"]` 추가
- `docker/docker-compose.monitor.yml` — grafana provisioning/dashboards 볼륨 + prometheus alerts.yml 마운트
- `docs/monitoring.md` — 메트릭 카탈로그 + 실행 가이드

### Prometheus 노출 이름
Micrometer dot → snake_case + Counter `_total` 자동, Timer `_seconds` 자동.
- `quiz_active_rooms`, `quiz_websocket_sessions`, `quiz_rabbitmq_answers_queue_depth`
- `quiz_events_published_total{type}`, `quiz_events_received_total{type}`
- `quiz_answers_enqueued_total`, `quiz_answers_processed_total{outcome}`
- `quiz_answer_processing_duration_seconds_bucket`, `quiz_redis_pubsub_latency_seconds_bucket`, `quiz_game_advance_duration_seconds_bucket`

### 실행
```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.monitor.yml up -d
# Grafana:    http://localhost:3001 (admin/admin) → Dashboards → "Quiz Platform Overview"
# Prometheus: http://localhost:9090
# Alerts:     http://localhost:9090/alerts
```

### 의도적으로 안 한 것
- AOP Aspect 자동 계측 — 예외 경로 제어가 명시적인 직접 호출 방식 선호
- 고카디널리티 태그 (roomId/userId/quizId)
- Alertmanager 연동 (Prometheus UI `/alerts`에서만 확인)
- Distributed tracing (Zipkin/Tempo) — Step 6 이후 선택

### 다음 단계
Step 6 — Testcontainers 통합 테스트(Redis/Rabbit/Postgres 띄우고 전체 게임 사이클 검증) + k6 WebSocket 부하 테스트.

---

## 2026-04-12 — Step 6: 통합 테스트 + 부하 테스트

**진행 방식**: Plan 에이전트 → 사용자 확인(A) → 에이전트 2개(A: support + 시나리오 1-4, B: 시나리오 5-7 + k6) 병렬.

### 완료 항목

#### (A) Testcontainers 인프라 + 시나리오 1-4
- `AbstractIntegrationTest` — `@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` + Postgres 16 / Redis 7 / RabbitMQ 3.13 공유 컨테이너 (`withReuse(true)`) + `@DynamicPropertySource` + `@BeforeEach resetState()`
- `support/ContainerReset` — TRUNCATE CASCADE + FLUSHALL + queue purge (try/catch)
- `support/TestDataFactory` — createHost/createPlayer/createRoom/addQuiz 픽스처
- `support/StompTestClient` — `connect`(Authorization stub 헤더) / `subscribe`(BlockingQueue) / `send`
- `GameLifecycleIntegrationTest` — 3명 × 3퀴즈 전체 사이클, leaderboard 순서, answer count, room.status FINISHED 검증
- `DistributedAdvanceLockTest` — `GameStateStore`/`GameService` 인스턴스 2개(publisherId 다름)로 동시 advance → Redis SETNX 경쟁 검증 (한계: 단일 JVM)
- `ReconnectRecoveryTest` — disconnect → grace → 재 connect + join → `/user/queue/snapshot` 수신
- `DuplicateAnswerTest` — 같은 quizId 2회 send → `answerRepository.count()==1` + duplicate counter +1
- `build.gradle`: `test { maxParallelForks = 1 }` (공유 컨테이너 때문)
- `application-test.yml`: ddl-auto=create-drop, 로그 WARN, allowed-origins "*"

#### (B) 시나리오 5-7 + k6 부하 테스트
- `RabbitDlqTest` — 존재하지 않는 quizId로 직접 enqueue → 10초 폴링으로 `quiz.answers.dlq` 도달 검증
- `PubSubSelfSkipTest` — Redis `convertAndSend`로 직접 발행(자기 publisherId) → 구독자 queue poll null (self-skip) / 다른 publisherId면 수신
- `TimerExpirationTest` — timeLimit=2초 퀴즈, 답변 없음 → GameWatchdog 1초 주기로 advance → 다음 QUIZ_PUSHED 수신
- `loadtest/k6/lib/stomp.js` — STOMP 프레임 buildConnect/Subscribe/Send/Disconnect/parseFrame ES module
- `loadtest/k6/game-load.js` — 5단계 ramp(50→500→1000 VU, 17분), setup()에서 방 10개×퀴즈 5개 REST 생성, VU별 roomId 분산, QUIZ_PUSHED 수신 시 답변
- `loadtest/README.md` — k6 설치/실행/Grafana 연동 가이드
- `docs/load-test-result.md` — 실행 결과 기록 템플릿 (사용자가 채움)
- `docs/testing.md` — 통합 테스트 전반 가이드

### 한계 명시 (포트폴리오용 솔직함)
- **분산 advance 테스트의 한계**: 단일 JVM에서 인스턴스 2개 수동 생성. 실제 2-pod 쿠버네티스 환경과 같지 않음. Redis SETNX 경쟁만 검증 가능.
- **k6 SockJS 우회**: 테스트 부담 줄이려 raw WebSocket 경로 `/ws/websocket` 사용. 실제 프론트 SockJS 폴링 fallback은 부하 테스트 범위 밖.
- **부하 테스트 실행은 사용자 수동**: k6는 외부 바이너리. CI 통합은 별도 스프린트.

### 실행
```bash
# 통합 테스트 (Docker Desktop 필요)
cd backend && ./gradlew test --tests 'com.quiz.integration.*'

# 부하 테스트 (k6 설치 필요)
cd loadtest/k6 && k6 run game-load.js
```

### 프로젝트 종료
Step 1-6 모두 완료. 포트폴리오로 제출 가능한 상태.

다음 확장(선택적):
- Step 7: JWT 인증 전환 (Step 4 임시 `hostId` 파라미터 제거)
- Step 8: OAuth2 GitHub 로그인
- Step 9: 게임 결과 PDF 생성 (iText)
- Step 10: 분산 트레이싱 (Zipkin/Tempo)
- Step 11: CI/CD (GitHub Actions) + AWS 배포

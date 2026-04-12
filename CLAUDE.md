# CLAUDE.md

Claude Code가 이 프로젝트에서 작업할 때 참조하는 가이드.

## 프로젝트 개요

**멀티플레이어 실시간 퀴즈 플랫폼** (Kahoot 스타일)
- 핵심 목적: WebSocket 분산 처리 / 동시성 / 실시간 이벤트 파이프라인을 직접 구현해 **백엔드 포트폴리오**로 제출
- 단순 기능 구현이 아니라 **분산 시스템 설계 역량 증명**이 목표

## 기술 스택

### Backend
- Spring Boot 3.3.x (Java 21, Gradle)
- Spring WebSocket (STOMP) / Security / Data JPA / Data Redis / AMQP / Actuator
- PostgreSQL 16, Redis 7, RabbitMQ 3
- Micrometer + Prometheus

### Frontend (테스트 클라이언트)
- Vite + React 18 (최소 구성)
- @stomp/stompjs + sockjs-client로 STOMP 연결 테스트

### Infra
- Docker Compose (postgres + redis + rabbitmq + prometheus + grafana)
- Nginx (배포 시 리버스 프록시)
- GitHub Actions CI/CD

## 디렉토리 구조

```
jaejinu_project001/
├── backend/             # Spring Boot (Gradle)
│   └── src/main/java/com/quiz/
│       ├── domain/      # 도메인별 패키지 (room, quiz, participant, answer, leaderboard)
│       ├── infra/       # redis, rabbitmq, websocket
│       ├── api/         # REST 컨트롤러
│       ├── config/      # Security, CORS, WebSocket
│       └── monitoring/  # Metrics, Health
├── frontend/            # Vite + React 테스트 클라이언트
├── docker/              # docker-compose + nginx conf
├── docs/                # 아키텍처/진행 로그/API 스펙
└── .github/workflows/   # CI/CD (추후)
```

## 실행 방법

```bash
# 1. 인프라 띄우기 (PostgreSQL + Redis + RabbitMQ)
cd docker && docker compose up -d

# 2. 백엔드 실행 (local 프로파일)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
# 최초 1회: gradle wrapper 설치 필요 → `gradle wrapper` (로컬 gradle 설치되어 있어야 함)

# 3. 프론트엔드 테스트 클라이언트
cd frontend && npm install && npm run dev
```

## 개발 원칙

- **도메인형 패키지 구조**: 레이어(controller/service/repository)가 아니라 도메인(room/quiz/...)으로 묶는다.
- **분산 가능성 우선**: 로컬 단일 서버에서만 돌아가는 코드 금지. 무상태(stateless) 서버 + Redis Pub/Sub 브리지를 전제로 설계.
- **WebSocket 세션 관리**: 인메모리 Map에 저장하지 말고 Redis에 TTL로 관리.
- **답변 처리**: 동기 처리하지 말고 RabbitMQ 큐로 비동기 처리. 백프레셔 대응.
- **리더보드**: DB 쿼리 금지. Redis Sorted Set (ZADD/ZRANGE)로만.
- **점수 계산**: `score = (int)(1000 * (1 - (responseTimeMs / (timeLimit * 1000.0))))`

## 진행 단계

세부 진행 로그는 [docs/progress.md](./docs/progress.md) 참조.

1. ✅ **프로젝트 초기화** — Gradle + 패키지 구조 + docker-compose + 프론트 스캐폴딩
2. ✅ **도메인 엔티티** — User, QuizRoom, Quiz, Participant, Answer + JPA Auditing + ScoreCalculator + LeaderboardKeys
3. ✅ **WebSocket + Redis Pub/Sub 이중 경로** — STOMP `/ws` + 로컬 직접 전달 + Redis 브리지 + self-skip
4. ✅ **핵심 게임 로직** — REST 방/퀴즈 + STOMP join/start/answer/leave + RabbitMQ 답변 파이프라인 + Redis Sorted Set 리더보드 + 타이머/워치독 + 30초 재접속 복구
5. ✅ **Prometheus 메트릭 + Grafana 대시보드** — `com.quiz.monitoring` 6개 빈 + 5군데 훅 + alerts.yml + provisioning
6. ⏳ k6 부하 테스트 + Testcontainers 통합 테스트

## Claude Code 작업 시 주의사항

- **문서화**: 각 단계 완료 후 `docs/progress.md`에 요약 추가.
- **코드 스타일**: Lombok 사용(`@Getter`, `@RequiredArgsConstructor`, `@Builder`). Setter 지양.
- **설정값**: 하드코딩 금지. `application.yml`의 프로파일(`local`/`prod`)로 분리.
- **커밋**: 사용자가 명시적으로 요청할 때만.

## 작업 진행 방식 (Step 3부터 적용)

큰 단계는 **Plan 에이전트 → 사용자 확인 → 병렬 구현 에이전트 × N → 메인 통합**:
1. Plan 에이전트 → 파일 목록·시그니처·결정사항 설계서
2. 사용자가 결정사항 리뷰 & 승인
3. 독립적인 파일 묶음을 구현 에이전트 여러 개에 병렬 분배 (예: WebSocket / Redis / Config)
4. 메인 세션이 통합 검증, 누락된 크로스-커팅 이슈 수정, 문서 갱신

## 알려진 함정 / 주의

- **Lombok `@RequiredArgsConstructor` + `@Qualifier`**: 필드에만 `@Qualifier`를 붙이면 Lombok이 생성자 파라미터에 복사하지 않아 주입이 깨진다. **`backend/lombok.config`에 `lombok.copyableAnnotations += Qualifier` 선언되어 있음 — 이 설정을 지우지 마라.**
- **Spring Security starter**: `SecurityConfig.filterChain`이 모든 경로를 `permitAll`로 열어둔 상태. Step 4에서 JWT 필터로 교체.
- **이중 경로 브로드캐스트**: `RoomEventPublisher.publish()`는 로컬 `SimpMessagingTemplate`과 Redis 양쪽으로 발행. `RoomEventSubscriber`는 `publisherId` 같으면 skip. 이 불변식을 깨지 마라 — 한 쪽만 쓰면 로컬 유저가 메시지를 못 받거나 중복 수신.
- **STOMP 인증 토큰 포맷 (현재 스텁)**: `Bearer stub:<userId>:<nickname>:<role>`. Step 5에서 실 JWT로 교체 시 `AuthTokenResolver` 인터페이스 구현체만 바꾸면 됨.
- **REST 인증 임시 방식 (Step 4)**: `POST /api/rooms` 요청 바디의 `hostId`, `POST /api/rooms/{id}/quizzes?hostId=` 쿼리 파라미터 — Step 5에서 JWT Principal로 치환 예정. 제거될 필드.
- **`GameService` 트랜잭션**: `start/advance/finish/onAnswerCounted` 모두 `@Transactional`. `onAnswerCounted`는 self-invocation 대비 명시적으로 붙여둠. `publishAfterCommit`은 DB 커밋 이후 이벤트 발행을 보장하므로 broadcast 메서드는 이것만 사용.
- **CORS**: `CorsConfigurationSource` 빈으로 Security 필터 앞단에서 적용. `addCorsMappings` 방식은 preflight가 Security에 막힐 수 있어 사용하지 않음.
- **advance 싱글톤**: `GameStateStore.tryAcquireAdvanceLock` (`SET NX PX 5s`, 소유자=publisherId). 로컬 타이머/Watchdog/전원제출 어디서 호출해도 1회만 전이.
- **답변 처리 idempotency**: `Answer` DB unique(`participant_id, quiz_id`) + `AnswerProcessor` 사전 existsBy + `DataIntegrityViolationException` catch. Rabbit retry 안전.

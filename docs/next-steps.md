# 이어받기 가이드

다음 세션에서 이 프로젝트를 이어받을 때 **먼저 읽을 파일**.

현재 커밋: `685d4aa` (Step 11 완료). 브랜치: `main`. 리포: https://github.com/jaejinu/quiz-platform

---

## 지금 어디까지 왔나

Step 1~11 **코드/인프라 수준**에서 전부 완료. 하지만 아래 "검증 안 된 것 / 수동 작업"이 남아있다.

Step별 요약은 [`CLAUDE.md`의 "진행 단계"](../CLAUDE.md) 또는 [`docs/progress.md`](./progress.md) 참조.

---

## 진행 중인 작업 (2026-04-12 기준)

### CI 수정 (라운드 6 진행 중)

Step 11 push 직후 CI가 줄줄이 실패해서 **6 라운드**에 걸쳐 수정 중.
상세 로그: [`docs/ci-fix-log.md`](./ci-fix-log.md)

| 라운드 | 문제 | 커밋 | 상태 |
|---|---|---|---|
| 1 | `DistributedAdvanceLockTest` GameService 10-arg 불일치 | `eb57eaa` | ✅ |
| 2 | `frontend/package-lock.json` 부재로 npm cache 실패 | `eb57eaa` | ✅ |
| 3 | Gradle 9: `junit-platform-launcher` 명시 요구 | `192d51c` | ✅ |
| 4 | OAuth2 빈 client-id 예외 → autoconfig 제외 시도 | `ec9f337` | ❌ (부작용) |
| 5 | Dummy GitHub registration 주입 | `43de40b` | ✅ context 로딩 |
| 6 | Testcontainers Singleton Pattern 전환 | `412c924` | ⏳ 실행 중 |

**다음 세션 진입 시 먼저 할 것**:
1. `gh run list --limit 5` — CI 결과 확인
2. 성공이면 → 아래 "확장" 작업 중 택일
3. 실패면 → `gh run download <id>` + artifact 분석 → 라운드 7+

---

## 검증 안 된 것 (중요)

이 프로젝트는 **코드를 실제로 실행·빌드·테스트한 적이 없다**. 다음 중 하나부터 시작 권장.

### 1. 로컬 빌드/실행 (최우선)
```bash
# 인프라
cd docker && docker compose up -d

# 백엔드 (Gradle wrapper가 없음 — 먼저 생성 필요)
cd ../backend
gradle wrapper --gradle-version 8.10   # 최초 1회
./gradlew bootRun --args='--spring.profiles.active=local'

# 프론트
cd ../frontend
npm install && npm run dev
```

**예상 이슈**:
- Gradle wrapper가 리포에 커밋 안 됨 (Agent 환경에 Gradle 없어서 생성 불가였음). `gradle wrapper` 명령으로 생성해서 커밋하면 CI도 `./gradlew` 쓸 수 있음.
- Spring Boot 3.3 + jjwt 0.12 + OpenTelemetry BOM 조합의 의존성 충돌 가능 (에이전트가 컴파일 검증 못 함)
- 일부 import 경로/시그니처 불일치 가능성 (특히 Step 4 병렬 4에이전트 부분)

### 2. Step 6 통합 테스트 실행
```bash
cd backend && ./gradlew test --tests 'com.quiz.integration.*'
```
7개 시나리오가 실제로 통과하는지 확인. Docker Desktop 필요.

### 3. GitHub Actions CI 첫 실행
현재 `main`에 push 됐으므로 이미 CI 돌았을 것. https://github.com/jaejinu/quiz-platform/actions 확인 → 실패 시 로그 읽고 수정.

---

## 수동으로 해야 할 작업

### 로컬 개발 시
1. **Gradle wrapper 생성** (위 참조) → 커밋
2. **frontend `package-lock.json` 생성** — 첫 `npm install` 후 생성됨, 커밋 권장 (CI에서 `npm ci` 쓰려면 필요)

### 프로덕션 배포 시 (`docs/deployment.md` 참조)
1. **GitHub OAuth App 등록** (`docs/oauth-setup.md`)
2. **JWT secret** 생성: `openssl rand -base64 48`
3. **DB/RabbitMQ 비밀번호** 생성 (secure random)
4. **도메인 + SSL** (Let's Encrypt)
5. **GHCR 패키지 public 전환** — 첫 release 후 수동
6. **GitHub OAuth App callback URL** 프로덕션용 별도 등록: `https://<domain>/login/oauth2/code/github`

---

## 알려진 함정 / 한계

### 코드 관련
- **Noto Sans KR 폰트 16MB** — CJK 풀셋 다운로드됨. 한글 전용 서브셋으로 교체하면 ~500KB (Step 9). Git repo 크기 영향.
- **분산 advance 테스트 한계** — `DistributedAdvanceLockTest`는 단일 JVM 내 인스턴스 2개 생성. 실제 2-pod 환경과 차이 있음.
- **URL fragment 토큰 XSS 노출** — Step 8의 `#token=...` redirect는 브라우저 history에 남음. 운영 전 `HttpOnly sameSite cookie` 전환 필요 (`docs/auth.md` 참조).
- **refresh token 미구현** — 1시간 만료 시 재로그인 (현재는 범위 외로 둠).
- **이메일 계정 병합 미지원** — LOCAL 계정과 GitHub 동일 이메일일 때 `email_conflict` 에러 (병합 Story는 별도).

### 테스트 관련
- **Step 6 통합 테스트가 Step 7~10에서 한 번도 실행 안 됨** — 각 Step마다 "0 수정" 원칙 세웠지만 실제 검증 미실행. 다음 세션에서 먼저 `./gradlew test` 돌려서 초록 확인.
- **k6 부하 테스트 미실행** — 결과는 사용자가 `docs/load-test-result.md`에 기록.

### 인프라 관련
- **Testcontainers reuse 활성화 필요**: `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` (선택, 2회차부터 빠름).
- **Tempo 리텐션 24시간 (로컬)** — 프로덕션은 별도 저장소 연결 고려.
- **RabbitMQ management UI 포트 15672** — 프로덕션 compose에서 미노출. SSH 터널 가이드 필요.

---

## 다음에 하면 좋을 확장

우선순위 순:

### 🔥 단기 (High Priority)
1. **실 빌드/테스트/실행 검증** — 위 "검증 안 된 것" 1, 2, 3
2. **Gradle wrapper 커밋** — CI `./gradlew` 동작하도록
3. **의존성 충돌 해결** — `gradle dependencies`로 확인 후 수정
4. **Step 6 테스트 실제로 모두 초록** — 7개 시나리오 통과 확인 + 실패 시 수정

### 🌱 중기 (Medium Priority)
5. **refresh token** — JWT 1시간 만료 UX 개선
6. **HttpOnly sameSite cookie** — fragment redirect 보안 강화
7. **Swagger/OpenAPI** — `/api/**` 자동 문서화 (springdoc-openapi)
8. **이메일 계정 병합** — OAuth + LOCAL 동일 이메일 정책
9. **Flyway/Liquibase** — 스키마 마이그레이션 (`ddl-auto=validate` 전제)
10. **Noto Sans KR 서브셋** — 16MB → ~500KB (pyftsubset 또는 fonttools)

### 🌟 장기 (Low Priority / 여유 있을 때)
11. **Grafana Loki** — 로그 집계 + trace-to-logs 링크
12. **Alertmanager** — Slack/Discord 알림
13. **Kubernetes manifests** — ECS/EKS 배포 시
14. **Terraform** — IaC (AWS)
15. **분산 트레이싱 Service Graph** — Tempo metrics_generator 활성화
16. **게임 방 캐싱** — FINISHED 방 결과 PDF를 Redis에 캐시 (재생성 비용 절감)
17. **푸시 알림** — WebPush (PWA)
18. **관전 모드 (OBSERVER)** — Quiz Platform 기획서에 있던 확장

---

## 면접/포트폴리오용 스토리텔링 포인트

프로젝트 README와 이 문서를 기반으로 면접 준비:

### 기술적 깊이
- **WebSocket 분산 처리**: Redis Pub/Sub 이중 경로 + self-skip (Step 3)
- **동시성 제어**: Redis `SETNX PX`로 advance 싱글톤 (Step 4)
- **비동기 파이프라인**: RabbitMQ 답변 처리 + DLQ + idempotency (Step 4)
- **Observability 스택**: Prometheus (메트릭) + Tempo (트레이싱) + 추후 Loki 추가 가능 (Step 5, 10)
- **Test Pyramid**: Testcontainers 통합 7개 + k6 부하 (Step 6)

### 설계 결정 (답변 준비)
- "왜 STOMP 골랐나?" → 구독 추상화, CONNECT 프레임 인증, 다중 채널
- "왜 OpenHtmlToPdf?" → iText AGPL 회피 + HTML 템플릿 유연성 (Step 9)
- "왜 OTel + Tempo?" → Spring Boot 3.x 표준, 벤더 중립 (Step 10)
- "왜 단일 서버 compose?" → 포트폴리오 스코프, k8s는 오버엔지니어링
- "병렬 advance 어떻게 보장?" → Redis `SET NX PX` + publisherId 기반 lock

### 한계 인정 (솔직함)
- 분산 advance 테스트는 단일 JVM 흉내
- URL fragment 토큰 보안 트레이드오프
- 수동 배포 (ECS/EKS 미사용)

---

## 파일 맵 (빠른 참조)

```
jaejinu_project001/
├── CLAUDE.md                  ← Claude Code 진입점 (Step 진행 + 함정)
├── README.md                  ← 프로젝트 개요
├── THIRD_PARTY_LICENSES.md    ← 라이선스 고지
├── .env.prod.sample           ← 프로덕션 env 템플릿
├── docs/
│   ├── next-steps.md          ← 이 파일 (이어받기)
│   ├── progress.md            ← Step 1-11 상세 진행 로그
│   ├── architecture.md        ← 시스템 다이어그램 + 설계 결정
│   ├── auth.md                ← JWT 인증 흐름 (Step 7)
│   ├── oauth-setup.md         ← GitHub OAuth App 등록 (Step 8)
│   ├── step9-pdf.md           ← PDF 결과지 API (Step 9)
│   ├── observability/tracing.md ← Tempo 트레이싱 (Step 10)
│   ├── monitoring.md          ← Prometheus/Grafana (Step 5)
│   ├── testing.md             ← 통합 테스트 가이드 (Step 6)
│   ├── deployment.md          ← 프로덕션 배포 (Step 11)
│   ├── cicd.md                ← GitHub Actions (Step 11)
│   └── load-test-result.md    ← k6 결과 템플릿
├── backend/                   ← Spring Boot 3.3 Java 21
│   ├── src/main/java/com/quiz/
│   │   ├── auth/              ← JWT + OAuth2 (Step 7, 8)
│   │   ├── api/               ← REST 컨트롤러
│   │   ├── domain/            ← 도메인 (room/quiz/participant/answer/leaderboard/game/result/user)
│   │   ├── infra/             ← redis/rabbitmq/websocket/pdf/tracing
│   │   ├── config/            ← Security/Jpa/Jackson/Game/Scheduling 등
│   │   ├── common/            ← exception/cors/security
│   │   └── monitoring/        ← Prometheus 커스텀 메트릭
│   ├── src/test/java/com/quiz/integration/  ← 7개 시나리오 + support
│   ├── src/main/resources/
│   │   ├── application{,-local,-prod,-test}.yml
│   │   ├── fonts/NotoSansKR-Regular.otf (16MB)
│   │   └── logback-spring.xml
│   ├── Dockerfile
│   ├── build.gradle
│   └── lombok.config          ← @Qualifier copyable 설정 (건드리지 말 것)
├── frontend/                  ← Vite + React 18
│   ├── src/
│   │   ├── App.jsx / AuthGate.jsx / GameApp.jsx / lib/api.js
│   │   ├── styles.css
│   ├── Dockerfile / nginx.conf
│   └── vite.config.js
├── docker/
│   ├── docker-compose.yml              ← 개발 인프라 (postgres/redis/rabbitmq)
│   ├── docker-compose.monitor.yml      ← 모니터링 (prometheus/grafana/tempo)
│   ├── docker-compose.prod.yml         ← 프로덕션 전체 스택
│   ├── prometheus/{prometheus,prometheus-prod,alerts}.yml
│   ├── grafana/{provisioning,dashboards}/
│   ├── tempo/tempo.yml
│   └── nginx/nginx.conf
├── loadtest/
│   ├── README.md
│   └── k6/{game-load.js,lib/stomp.js}
└── .github/workflows/
    ├── ci.yml                 ← gradle test + vite build
    └── release.yml            ← GHCR 이미지 push (matrix: backend/frontend)
```

---

## 다음 세션 진입 시 추천 프롬프트

```
docs/next-steps.md 읽고 현재 상태 파악해줘.
그리고 [원하는 작업] 진행하자.
```

예시:
- "Gradle wrapper 생성하고 로컬에서 `./gradlew bootRun`이 뜨는지 확인하자"
- "Step 6 통합 테스트 실행해서 실패하는 것부터 고치자"
- "refresh token 추가 설계해줘"
- "Noto Sans KR 서브셋으로 교체하자"

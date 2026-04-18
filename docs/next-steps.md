# 이어받기 가이드

다음 세션에서 이 프로젝트를 이어받을 때 **먼저 읽을 파일**.

현재 커밋: `644956f` (로컬 빌드/테스트 검증 완료). 브랜치: `main`. 리포: https://github.com/jaejinu/quiz-platform

---

## 지금 어디까지 왔나

Step 1~11 **코드/인프라 수준**에서 전부 완료. 하지만 아래 "검증 안 된 것 / 수동 작업"이 남아있다.

Step별 요약은 [`CLAUDE.md`의 "진행 단계"](../CLAUDE.md) 또는 [`docs/progress.md`](./progress.md) 참조.

---

## CI 상태 ✅

**GREEN** — Step 11 push 후 **7 라운드** 수정 끝에 main이 CI 통과.
상세 로그: [`docs/ci-fix-log.md`](./ci-fix-log.md)

| 라운드 | 문제 | 커밋 | 결과 |
|---|---|---|---|
| 1 | `DistributedAdvanceLockTest` GameService 10-arg 불일치 | `eb57eaa` | ✅ |
| 2 | `frontend/package-lock.json` 부재 | `eb57eaa` | ✅ |
| 3 | Gradle 9: `junit-platform-launcher` 명시 요구 | `192d51c` | ✅ |
| 4 | OAuth2 autoconfig exclude (부작용 발생) | `ec9f337` | ❌ |
| 5 | Dummy GitHub registration 주입 | `43de40b` | ✅ context 로딩 |
| 6 | Testcontainers **Singleton Container Pattern** | `412c924` | ✅ 인프라 연결 |
| 7 | DuplicateAnswerTest 참가자 2명으로 advance 회피 | `fb43541` | ✅ **GREEN** |

이제 main 브랜치는 매 push마다 CI 자동 검증됨. Release 워크플로우도 항상 성공 중 (이미지 GHCR에 push됨).

---

## 검증 완료 ✅

**2026-04-18**: 로컬 빌드/실행/테스트 모두 검증 완료.

### 검증 결과
| 항목 | 결과 |
|---|---|
| `./gradlew compileJava` | ✅ 컴파일 성공 |
| `./gradlew build -x test` | ✅ JAR 빌드 성공 |
| `./gradlew test` (통합 테스트 7개) | ✅ 전체 통과 |
| `docker compose up -d` + `bootRun` | ✅ 서버 기동, Health UP |
| HOST 로그인 + JWT 발급 | ✅ `/api/auth/login` 정상 |
| `npm run dev` (프론트) | ✅ Vite dev server 정상 |
| `npm run build` (프론트) | ✅ production 빌드 성공 |

### 검증 과정에서 수정한 이슈
1. **Gradle wrapper 부재** → Gradle 8.14 wrapper 추가 + foojay toolchain resolver (Java 21 자동 프로비저닝)
2. **Testcontainers Docker 29.x 비호환** → Spring Boot BOM이 Testcontainers를 1.19.8로 강제 → 내부 shaded docker-java가 API 1.43 사용 → Docker 29.x가 400 반환. `docker-java.properties`로 API 1.44 강제 + BOM 버전 오버라이드 (1.21.1)
3. **RabbitMQ ARM64 비호환** → `rabbitmq:3.13-management-alpine`이 amd64 전용 → `rabbitmq:4.0-management-alpine`으로 변경
4. **RabbitAdmin 빈 미등록** → `RabbitConfig`에 명시적 `@Bean` 추가
5. **OAuth2 GitHub 설정 누락** → `application-local.yml`에 dummy client-id/secret 기본값 추가

---

## 수동으로 해야 할 작업

### 로컬 개발 시
1. ~~**Gradle wrapper 생성**~~ → ✅ 완료 (`644956f`)
2. **frontend `package-lock.json` 커밋** — `npm install` 후 생성됨, CI에서 `npm ci` 쓰려면 커밋 권장

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
- ~~**Noto Sans KR 폰트 16MB**~~ → ✅ 1.2MB 서브셋으로 교체 완료
- **분산 advance 테스트 한계** — `DistributedAdvanceLockTest`는 단일 JVM 내 인스턴스 2개 생성. 실제 2-pod 환경과 차이 있음.
- **URL fragment 토큰 XSS 노출** — `#token=...` redirect는 브라우저 history에 남음. HttpOnly cookie 전환 설계는 `docs/auth.md` 참조.
- ~~**refresh token 미구현**~~ → ✅ 구현 완료 (14일 TTL, rotation, 탈취 감지).
- ~~**이메일 계정 병합 미지원**~~ → ✅ 구현 완료 (동일 이메일 자동 OAuth 연결).

### 테스트 관련
- ~~**Step 6 통합 테스트가 Step 7~10에서 한 번도 실행 안 됨**~~ → ✅ 2026-04-18 로컬에서 7개 시나리오 전체 통과 확인
- **k6 부하 테스트 미실행** — 결과는 사용자가 `docs/load-test-result.md`에 기록.

### 인프라 관련
- **Testcontainers reuse 활성화 필요**: `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` (선택, 2회차부터 빠름).
- **Tempo 리텐션 24시간 (로컬)** — 프로덕션은 별도 저장소 연결 고려.
- **RabbitMQ management UI 포트 15672** — 프로덕션 compose에서 미노출. SSH 터널 가이드 필요.

---

## 다음에 하면 좋을 확장

우선순위 순:

### 🔥 단기 (High Priority)
1. ~~**실 빌드/테스트/실행 검증**~~ → ✅ 완료
2. ~~**Gradle wrapper 커밋**~~ → ✅ 완료
3. ~~**의존성 충돌 해결**~~ → ✅ 완료 (Testcontainers BOM 오버라이드)
4. ~~**Step 6 테스트 실제로 모두 초록**~~ → ✅ 7개 시나리오 통과

### 🌱 중기 (Medium Priority)
5. ~~**refresh token**~~ → ✅ 완료 (rotation + 탈취 감지 + 자동 갱신)
6. **HttpOnly sameSite cookie** — 범위 크고 가성비 낮아 보류 (설계 노트: `docs/auth.md`)
7. ~~**Swagger/OpenAPI**~~ → ✅ 완료 (`/swagger-ui/index.html`)
8. ~~**이메일 계정 병합**~~ → ✅ 완료 (OAuth + LOCAL 동일 이메일 자동 연결)
9. ~~**Flyway**~~ → ✅ 완료 (V1 초기 스키마, local/prod: validate + flyway)
10. ~~**Noto Sans KR 서브셋**~~ → ✅ 완료 (16MB → 1.2MB)

### 🌟 장기 (Low Priority / 여유 있을 때)
11. ~~**Grafana Loki**~~ → ✅ 완료 (Loki + Promtail + trace-to-logs)
12. ~~**Alertmanager**~~ → ✅ 완료 (Slack/Discord webhook 템플릿)
13. ~~**Kubernetes manifests**~~ → ✅ 완료 (Deployment/Service/HPA/Ingress)
14. ~~**Terraform**~~ → ✅ 완료 (VPC/ECS/RDS/ElastiCache/MQ 모듈)
15. ~~**분산 트레이싱 Service Graph**~~ → ✅ 완료 (Tempo metrics_generator 활성화)
16. ~~**게임 방 캐싱**~~ → ✅ 완료 (Redis 24시간 TTL)
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

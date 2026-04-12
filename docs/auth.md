# Step 7 — 인증 흐름 (JWT + Role)

Step 4~6까지 쓰이던 `Bearer stub:<userId>:<nickname>:<role>` 스텁 토큰을
실 JWT로 교체하고, 프로파일별 토큰 리졸버로 분기한다.

## JWT 발급 규격

- 알고리즘: **HS256** (대칭키, `application.yml`의 `quiz.jwt.secret`)
- TTL: **1시간** (`quiz.jwt.ttl=PT1H`)
- Claims:
  - `sub` — userId (Long)
  - `nickname` — String
  - `role` — `HOST` | `PLAYER`
  - `iat`, `exp` — 표준 클레임

발급 엔드포인트:

| Method | Path | 요청 | 응답 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | `{email, password, nickname}` | 201 `TokenResponse` |
| POST | `/api/auth/login` | `{email, password}` | 200 `TokenResponse` |

`TokenResponse`:
```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-04-12T12:34:56Z",
  "user": { "id": 1, "nickname": "alice", "role": "PLAYER" }
}
```

에러 응답:
```json
{ "code": "EMAIL_DUPLICATE", "message": "이미 가입된 이메일", "timestamp": "..." }
```

## 인증 헤더

- **REST**: `Authorization: Bearer <jwt>` 필수
  - 예외: `/api/auth/signup`, `/api/auth/login`, `/actuator/health`
  - 401 반환 시 프론트는 localStorage 비우고 reload → 로그인 화면 복귀
- **STOMP**: `CONNECT` 프레임의 `Authorization` 헤더에 동일 JWT
  - 기존 스텁 포맷을 그대로 교체 (`@stomp/stompjs` `connectHeaders`)

## 프로파일별 리졸버

`AuthTokenResolver` 인터페이스의 구현체를 프로파일로 스위칭:

| 프로파일 | 구현 | 용도 |
| --- | --- | --- |
| `local`, `prod` | `JwtAuthTokenResolver` | 실제 HS256 서명 검증 |
| `test` | `StubAuthTokenResolver` | 기존 `Bearer stub:<id>:<nick>:<role>` 포맷 유지 — 통합 테스트 **0 수정** 목표 |

이 분리는 Step 6에서 구축한 Testcontainers 시나리오(Happy / 분산advance / 재접속
/ 중복답변 / DLQ / SelfSkip / Timer)를 토큰 교체로 깨뜨리지 않기 위함.

## HOST 부트스트랩

- `HostSeedRunner` (`@Profile("local")`) 가 부팅 시 `host@local.dev` / `hostpass123`
  계정을 UPSERT 한다.
- 운영(`prod`) 프로파일에서는 실행되지 않는다.
- seed 비밀번호는 `application-local.yml`로 주입, 하드코딩 금지.

## 역할 기반 권한

- `POST /api/rooms` — **HOST only**
- `POST /api/rooms/{id}/quizzes` — **HOST only**
- `/app/room/{id}/start` — **HOST only** (WebSocket)
- 나머지 (`/join`, `/answer`, `/leave`, `/topic/room/{id}` 구독) — 인증된 모든 사용자

기존 `hostId` 바디 필드 / `?hostId=` 쿼리 파라미터는 제거되고, JWT의 `sub` +
`role` 클레임으로 대체된다.

## 프론트엔드 저장 방식

- `localStorage` 키: `quiz.jwt`, `quiz.user`
- 자동 부착 헬퍼: `src/lib/api.js`의 `fetchWithAuth(path, options)`

### 보안 주의

- **localStorage JWT는 XSS에 노출된다.** 악성 스크립트 주입 시 토큰 탈취 위험.
- 운영 전 반드시 **SameSite=Strict HttpOnly cookie** 방식으로 마이그레이션 필요.
- 현재는 테스트 클라이언트 / 포트폴리오 데모 범위로 단순화.
- Refresh token은 이번 스텝 범위 외 (향후 도입 시 rotating refresh + revocation list 고려).

## 프론트 플로우 요약

```
App.jsx
 ├─ localStorage 에 jwt/user 있으면 → <GameApp>
 └─ 없으면 → <AuthGate>
                ├─ LoginForm  → POST /api/auth/login  → onAuth(tokenResponse)
                └─ SignupForm → POST /api/auth/signup → onAuth(tokenResponse)

onAuth → localStorage 저장 → 상태 갱신 → <GameApp> 렌더
로그아웃 → localStorage 제거 → <AuthGate> 복귀
401 수신 → fetchWithAuth 가 자동 로그아웃 + reload
```

# Quiz Platform — Test Client

STOMP over WebSocket 연결 테스트용 최소 React 앱.

## 실행

```bash
npm install
npm run dev
# http://localhost:5173
```

Vite dev 서버의 프록시 설정으로 `/api`, `/ws` 요청이 `http://localhost:8080` (백엔드)로 전달됨.

## 구성

- `@stomp/stompjs` + `sockjs-client`로 STOMP 연결
- Room ID 입력 → Connect → `/topic/room/{roomId}` 구독
- 수신 메시지를 화면에 실시간 출력

Step 3(백엔드 WebSocket 설정) 완료 전까지는 연결 실패가 정상.

## 로그인

백엔드 실행 후 `host@local.dev` / `hostpass123` HOST 계정이 자동 seed 됩니다.
일반 플레이어는 회원가입 → PLAYER 역할로 가입됩니다.

- 로그인/회원가입 성공 시 JWT가 `localStorage`(`quiz.jwt`, `quiz.user`)에 저장됩니다.
- 모든 REST 호출은 `Authorization: Bearer <jwt>` 자동 부착 (`src/lib/api.js`의 `fetchWithAuth`).
- STOMP `CONNECT` 프레임의 `Authorization` 헤더에도 동일 JWT가 실립니다.
- 401 수신 시 로컬 스토리지를 비우고 새로고침하여 로그인 화면으로 복귀합니다.
- `POST /api/rooms`, `POST /api/rooms/{id}/quizzes`, `/start`는 **HOST 역할**만 허용됩니다. PLAYER 계정에서는 버튼이 비활성화됩니다.
- **GitHub으로 로그인**: GitHub OAuth App 등록 필요 (`docs/oauth-setup.md` 참조). 환경변수 `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` 설정 후 백엔드 재시작.

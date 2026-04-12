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

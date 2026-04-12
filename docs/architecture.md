# Architecture

살아있는 문서. 설계가 바뀌면 여기서 먼저 바꾼다.

## 시스템 구성도

```
          [Browser / React Test Client]
                     │
                     │ HTTP / WebSocket(STOMP over SockJS)
                     ▼
                [Nginx (배포 시)]
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
   [Spring Boot #1]          [Spring Boot #2]   ← 수평 확장
        │                         │
        │  WebSocket 세션은 각 서버가 소유
        │
        └──────────┬──────────────┘
                   ▼
       ┌───────────────────────┐
       │   Redis (Pub/Sub)     │  ← 서버 간 브로드캐스트 브리지
       │   Redis (Sorted Set)  │  ← 실시간 리더보드
       │   Redis (Session)     │  ← WebSocket 세션 TTL 관리
       └───────────────────────┘
                   │
                   ▼
       ┌───────────────────────┐
       │       RabbitMQ        │  ← 답변 처리 큐 (비동기)
       └───────────────────────┘
                   │
                   ▼
       ┌───────────────────────┐
       │    PostgreSQL 16      │  ← 영구 저장
       └───────────────────────┘
```

## 핵심 설계 결정

### 1. STOMP over WebSocket (not raw WebSocket)
- 채널 구독(`/topic/room/{roomId}`) 추상화가 자연스럽다
- CONNECT 프레임 헤더로 JWT 인증 가능
- 구독 토픽별로 메시지 라우팅 자동

### 2. Redis Pub/Sub으로 서버 간 브리지
**문제**: 서버 2대 이상이면 유저 A는 서버1, 유저 B는 서버2에 붙을 수 있다. 서버1에서 `SimpMessagingTemplate.convertAndSend`를 해도 서버2 유저에게 안 간다.

**해결**: 서버 내 broadcast 대신, Redis 채널에 publish → 모든 서버가 구독 → 각 서버의 `SimpMessagingTemplate`로 로컬 세션에 전달.

### 3. 답변은 RabbitMQ로 비동기 처리
- 동시 1000명이 답변을 찍으면 정답 판정 + 점수 계산 + 리더보드 업데이트가 동기로 블로킹됨
- 큐에 넣고 워커가 처리, 결과는 다시 Redis Pub/Sub으로 브로드캐스트

### 4. 리더보드는 Redis Sorted Set
- DB 조회 금지. `ZADD leaderboard:{roomId} score userId` / `ZRANGE ... WITHSCORES`
- 게임 종료 시에만 최종 결과를 PostgreSQL로 flush

### 5. WebSocket 세션 복구
- 연결 끊김 감지 → 세션 ID를 Redis에 TTL 30초로 유지
- 재접속 시 세션 ID 제시 → 참가자 복구 + 현재 게임 상태 스냅샷 전송

## 도메인 모델

```
User           : id, email, nickname, role(HOST|PLAYER), oauthProvider
QuizRoom       : id, code(6자), status(WAITING|ACTIVE|FINISHED), hostId, maxPlayers, timeLimit
Quiz           : id, roomId, question, type(SINGLE|MULTIPLE|OX), options(JSON), correctAnswer, timeLimit, orderIndex
Participant    : id, roomId, userId, sessionId, status(CONNECTED|DISCONNECTED), joinedAt
Answer         : id, participantId, quizId, answer, isCorrect, responseTimeMs, score, submittedAt
Leaderboard    : Redis Sorted Set — key=leaderboard:{roomId}, member=userId, score=누적점수
```

## 엔드포인트 개요

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 일반 로그인 |
| GET | `/oauth2/authorization/github` | GitHub OAuth |
| POST | `/api/rooms` | 방 생성 (HOST) |
| GET | `/api/rooms/{code}` | 방 정보 조회 |
| POST | `/api/rooms/{id}/quizzes` | 퀴즈 등록 (HOST) |

### STOMP 엔드포인트

| Direction | Destination | 설명 |
|---|---|---|
| SEND | `/app/room/{roomId}/join` | 방 입장 |
| SEND | `/app/room/{roomId}/start` | 게임 시작 (HOST) |
| SEND | `/app/room/{roomId}/answer` | 답변 제출 |
| SUBSCRIBE | `/topic/room/{roomId}` | 방 브로드캐스트 (퀴즈, 리더보드, 상태 변경) |
| SUBSCRIBE | `/user/queue/private` | 개인 메시지 (재접속 스냅샷 등) |

세부 API 스펙은 `docs/api-spec.md` 참조 (작성 예정).

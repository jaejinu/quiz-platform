# Quiz Platform

실시간 멀티플레이어 퀴즈 플랫폼 — WebSocket 분산 처리 / 동시성 / 실시간 이벤트 파이프라인을 직접 구현한 백엔드 포트폴리오.

## 핵심 기술

- **Spring Boot 3.3** (Java 21) + STOMP over WebSocket
- **Redis Pub/Sub**으로 다중 서버 간 브로드캐스트 브리지
- **Redis Sorted Set**으로 실시간 리더보드
- **RabbitMQ**로 답변 처리 큐 (백프레셔 대응)
- **PostgreSQL** 영구 저장
- **Prometheus + Grafana**로 메트릭 수집/시각화

## 빠른 시작

```bash
# 1. 인프라 (Postgres, Redis, RabbitMQ) 실행
cd docker
docker compose up -d

# 2. 백엔드 실행
cd ../backend
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 테스트 프론트엔드
cd ../frontend
npm install && npm run dev
```

## 문서

- [아키텍처](./docs/architecture.md)
- [진행 로그](./docs/progress.md)
- [API 스펙](./docs/api-spec.md) (작성 예정)
- [Claude Code 가이드](./CLAUDE.md)

## 라이선스

개인 포트폴리오 프로젝트.

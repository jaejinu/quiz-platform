# 프로덕션 배포 가이드

## 대상 환경
- AWS EC2 t3.medium (2 vCPU, 4GB RAM) 또는 Lightsail $10 플랜 (2GB)
- Ubuntu 22.04 LTS
- 도메인 (Let's Encrypt SSL용)

## 1. 서버 준비

```bash
# Docker 설치
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
# 재로그인 필요

# 방화벽 (Lightsail/EC2 Security Group에서도 80/443 오픈)
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

## 2. 소스 배치

프로덕션은 **이미지를 GHCR에서 pull**하므로 소스 전체가 필요 없지만, `docker-compose.prod.yml`, `docker/prometheus/`, `docker/grafana/`, `docker/tempo/` 디렉토리는 필요.

```bash
git clone https://github.com/jaejinu/quiz-platform.git
cd quiz-platform
```

또는 필요한 파일만 scp:
```bash
scp -r docker/ user@server:/home/user/quiz-platform/
scp .env.prod.sample user@server:/home/user/quiz-platform/
```

## 3. 환경변수 작성

```bash
cp .env.prod.sample .env.prod
vim .env.prod
```

**필수 수정 항목**:
- `PUBLIC_HOST` — 실제 도메인 (예: `quiz.example.com`)
- `POSTGRES_PASSWORD` — `openssl rand -base64 32`
- `RABBITMQ_DEFAULT_PASS` — 위와 동일
- `QUIZ_JWT_SECRET` — `openssl rand -base64 48`
- `GITHUB_CLIENT_ID/SECRET` — GitHub OAuth App 생성 (callback: `https://${PUBLIC_HOST}/login/oauth2/code/github`)
- `QUIZ_WEBSOCKET_ALLOWED_ORIGINS` — `https://${PUBLIC_HOST}`
- `QUIZ_AUTH_POST_LOGIN_REDIRECT` — `https://${PUBLIC_HOST}/auth/callback`
- `GRAFANA_ADMIN_PASSWORD`

## 4. GHCR 이미지 pull

Public 이미지면 인증 불필요:
```bash
docker pull ghcr.io/jaejinu/quiz-platform-backend:latest
docker pull ghcr.io/jaejinu/quiz-platform-frontend:latest
```

Private이면 GitHub PAT(`read:packages` scope) 사용:
```bash
echo $GITHUB_PAT | docker login ghcr.io -u jaejinu --password-stdin
```

## 5. 기동

```bash
docker compose --env-file .env.prod -f docker/docker-compose.prod.yml up -d

# 로그 확인
docker compose -f docker/docker-compose.prod.yml logs -f backend
```

약 60~90초 후 `/actuator/health`가 UP으로 전환.

## 6. HTTPS (Let's Encrypt)

### 옵션 A: 호스트 nginx + certbot (권장)

compose의 frontend(포트 80)는 내부로 내리고 호스트 nginx가 reverse proxy:

```bash
# frontend 포트 변경: docker-compose.prod.yml에서 80:80 → 127.0.0.1:8081:80
sudo apt install -y nginx certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/quiz.conf`:
```nginx
server {
    listen 80;
    server_name quiz.example.com;
    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/quiz.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d quiz.example.com
```

### 옵션 B: Caddy (1-line SSL)
컨테이너로 Caddy 추가. `compose.yml`에 caddy 서비스 추가, `Caddyfile`에 `quiz.example.com { reverse_proxy frontend:80 }` 단 한 줄. 자세한 건 Caddy 공식 문서.

## 7. 업데이트 (무중단 근사)

```bash
docker compose --env-file .env.prod -f docker/docker-compose.prod.yml pull backend frontend
docker compose --env-file .env.prod -f docker/docker-compose.prod.yml up -d --no-deps backend frontend
```

백엔드는 `server.shutdown=graceful` + `stop_grace_period: 30s`로 **기존 WebSocket 세션을 30초까지 유지**하며 종료. 완벽한 zero-downtime은 아니지만 몇 초 수준의 diff.

## 8. 백업

```bash
# cron: 매일 03:00
0 3 * * * docker exec quiz-postgres pg_dump -U quiz quizdb | gzip > /backup/db-$(date +\%F).sql.gz
```

## 9. 모니터링 접속

내부망만 노출된 서비스는 SSH 터널로:
```bash
ssh -L 3001:localhost:3001 -L 9090:localhost:9090 user@server
# 브라우저 http://localhost:3001 (Grafana)
# 브라우저 http://localhost:9090 (Prometheus)
# RabbitMQ management는 compose.prod.yml에서 포트 미노출 — 필요 시 추가
```

## 10. 의도적 축소
- **Kubernetes/ECS**: 단일 노드 compose로 시작, 트래픽 증가 시 재평가
- **Terraform**: 수동 EC2 1대 기준 가이드. IaC는 필요 시 추가
- **Blue-Green/Canary**: graceful shutdown으로 대부분 커버, 완전 무중단은 ALB+ECS 필요
- **Secret Manager**: `.env.prod` 파일 관리. AWS SSM/SecretsManager 연동은 별도 Step

## 트러블슈팅
| 증상 | 원인 | 해결 |
|---|---|---|
| `/actuator/health` 401 | Security 설정 | `/actuator/health`는 permitAll — 다른 문제 확인 |
| STOMP 연결 실패 | nginx WebSocket 헤더 누락 | `Upgrade`/`Connection` 헤더 확인 |
| OAuth 400 | callback URL 불일치 | GitHub OAuth App URL을 `https://${PUBLIC_HOST}/...`로 |
| JWT secret error | 32바이트 미만 | `openssl rand -base64 48` 재생성 |
| Grafana 패스워드 기본 | 환경변수 미반영 | `.env.prod` `GRAFANA_ADMIN_PASSWORD` 확인 후 `grafana-data` 볼륨 삭제 후 재시작 |

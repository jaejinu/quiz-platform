# CI/CD 파이프라인

## 워크플로우

### `.github/workflows/ci.yml`
- 트리거: `main` push, PR
- Backend 잡: Gradle test (Testcontainers 포함)
- Frontend 잡: Vite build
- 실패 시 test 리포트를 아티팩트로 업로드

> 주의: 리포에 `./gradlew` (gradle wrapper)가 없어 `gradle/actions/setup-gradle@v4`가
> 제공하는 `gradle` 바이너리를 직접 호출한다. Wrapper 추가 시 `gradle test` → `./gradlew test`로 치환.

### `.github/workflows/release.yml`
- 트리거: `main` push, `v*` 태그, manual dispatch
- Matrix [backend, frontend] 병렬
- Docker Buildx로 linux/amd64 이미지 빌드
- GHCR (`ghcr.io/jaejinu/quiz-platform-{component}`)에 push
- 태그: `latest` (main 전용), `sha-{short}`, `v1.2.3`, `1.2`

## 이미지 태그 규칙

| 트리거 | 생성 태그 |
|---|---|
| main push | `latest`, `sha-abc1234` |
| `v1.2.3` 태그 | `v1.2.3`, `1.2.3`, `1.2`, `sha-abc1234` |
| dispatch | `sha-abc1234` |

## 수동 릴리스

```bash
git tag v0.1.0
git push --tags
```

또는 GitHub Actions UI → Release → Run workflow.

## GHCR 패키지 설정

첫 릴리스 후 GitHub 패키지 페이지에서:
- `https://github.com/users/jaejinu/packages/container/quiz-platform-backend/settings`
- **Change visibility → Public** (anonymous pull 허용)
- 리포지토리 링크 연결

## Secret 관리

현재 workflow가 사용하는 secret:
- `GITHUB_TOKEN` — 자동 주입 (repo scope)

추가 필요 시:
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` — AWS 배포
- `SSH_PRIVATE_KEY` — EC2 SSH 배포

## 캐시 전략

- Gradle 의존성: `gradle/actions/setup-gradle@v4` 자동
- npm: `actions/setup-node@v4` + `cache: npm`
- Docker 레이어: `cache-from/to: type=gha` (GitHub Actions 캐시)

첫 빌드 ~10분, 캐시 히트 시 2~3분.

## 로컬에서 release 워크플로우 테스트

`act` (https://github.com/nektos/act) 활용:
```bash
act push -W .github/workflows/release.yml
```

Docker-in-Docker 필요. 권장하지 않고 실제 GitHub에서 확인.

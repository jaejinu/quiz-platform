# GitHub OAuth 설정

Step 8 GitHub 로그인을 사용하려면 GitHub OAuth App 등록 + 환경변수 설정이 필요합니다.

## 1. GitHub OAuth App 등록

1. https://github.com/settings/developers 접속
2. 좌측 "OAuth Apps" → **"New OAuth App"** 클릭
3. 다음 정보 입력:
   - **Application name**: `Quiz Platform (Local)` (자유)
   - **Homepage URL**: `http://localhost:5173`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
     (**정확히 일치해야 함**. 오타 시 `redirect_uri_mismatch` 에러)
4. **Register application** 클릭
5. 생성된 페이지에서 **Client ID** 복사 (우측 상단)
6. **"Generate a new client secret"** 클릭 → Client Secret **즉시 복사** (페이지 이탈 시 다시 못 봄)

## 2. 환경 변수 설정

`backend/.env` 파일 생성 (이미 `.gitignore`에 포함됨):

```env
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxxxxxx
GITHUB_CLIENT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
QUIZ_AUTH_POST_LOGIN_REDIRECT=http://localhost:5173/auth/callback
```

또는 쉘에서 export:
```bash
export GITHUB_CLIENT_ID=...
export GITHUB_CLIENT_SECRET=...
```

**주의**: `.env` 파일은 절대 커밋하지 마세요. 커밋 대상은 `.env.sample`만.

## 3. 실행 확인

1. 백엔드 재시작: `./gradlew bootRun --args='--spring.profiles.active=local'`
2. 프론트: `npm run dev`
3. 브라우저 `http://localhost:5173` → "GitHub으로 로그인" 클릭
4. GitHub 인증 화면 → 승인
5. `http://localhost:5173/` 으로 자동 복귀 (JWT 발급 완료)

## 4. 프로덕션 배포

- Homepage URL / Callback URL을 실제 도메인 (HTTPS)으로 등록된 **별도 OAuth App** 사용
- Client ID/Secret은 배포 플랫폼 Secret Manager (AWS Secrets Manager, GitHub Actions Secrets 등)로 주입

## 5. 에러 해결

| 에러 | 원인 | 해결 |
|---|---|---|
| `redirect_uri_mismatch` | Callback URL 불일치 | GitHub OAuth App 설정 재확인 |
| `email_required` | GitHub 계정 public email 없음 | GitHub → Settings → Emails에서 primary public |
| `email_conflict` | 같은 이메일로 이미 일반 가입됨 | 기존 계정으로 로그인 (병합 미지원) |
| `oauth_failed` | 기타 OAuth 실패 | 로그 확인 (`com.quiz.auth.oauth` 레벨 DEBUG) |

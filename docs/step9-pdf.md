# Step 9 — 게임 결과 PDF

## 개요
게임이 `FINISHED` 상태로 전이되면 HOST는 결과 PDF를 다운로드할 수 있습니다.

## API
```
GET /api/rooms/{roomId}/result.pdf
Authorization: Bearer <jwt>  (role=HOST)
```

### 응답
- `200 OK` — `Content-Type: application/pdf`, 파일 `quiz-result-<roomCode>-<yyyyMMdd>.pdf`
- `400 NOT_FINISHED` — 게임이 아직 끝나지 않음
- `403` — HOST가 아니거나 해당 방의 HOST가 아님
- `404 ROOM_NOT_FOUND`

### 응답 헤더 예시
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="quiz-result-ABC123-20260412.pdf"; filename*=UTF-8''quiz-result-ABC123-20260412.pdf
```

## PDF 내용
1. **헤더**: 방 제목, 방 코드, HOST 닉네임, 시작/종료 시각 (Asia/Seoul), 참가자 수, 퀴즈 수
2. **최종 리더보드**: 전체 참가자의 순위/닉네임/누적 점수 (Redis Sorted Set 기반)
3. **퀴즈별 통계**: 문제, 정답, 정답률, 평균 응답시간
4. **가장 많이 틀린 문제 Top 3**
5. **푸터**: 생성 시각

## 구현
- 라이브러리: **OpenHtmlToPdf 1.0.10** (Apache 2.0) — HTML/CSS → PDF
- 폰트: **Noto Sans KR Regular** (OFL 1.1) — `backend/src/main/resources/fonts/NotoSansKR-Regular.otf`
- 백엔드 패키지:
  - `com.quiz.domain.result` — 집계 (GameResultService + DTO records)
  - `com.quiz.infra.pdf` — OpenHtmlPdfGenerator + GameResultHtmlRenderer
  - `com.quiz.api.result.GameResultController`

## 프론트엔드
- `frontend/src/GameApp.jsx`
  - `gameFinished` state: `GAME_FINISHED` 이벤트 수신 시 `true`, `sendStart` 또는 연결 끊김 시 `false` 리셋
  - HOST && `gameFinished`일 때만 "결과 PDF 다운로드" 버튼 노출
  - `downloadResultPdf()`: `fetchWithAuth`로 PDF fetch → `Content-Disposition` 헤더에서 파일명 파싱 → `Blob` → `<a download>` 강제 클릭

## 라이선스 고지
- OpenHtmlToPdf: Apache License 2.0
- Apache PDFBox, FontBox: Apache License 2.0
- Noto Sans KR: SIL Open Font License 1.1

재배포 시 해당 라이선스 원문을 프로젝트에 동봉해야 합니다 (`LICENSE` 또는 `THIRD_PARTY_LICENSES` 파일).

## 트러블슈팅
| 증상 | 원인 | 해결 |
|---|---|---|
| 한글이 □□□로 출력 | 폰트 로드 실패 | `resources/fonts/NotoSansKR-Regular.otf` 경로/용량 확인 |
| 400 NOT_FINISHED | 게임 미종료 | HOST가 `/start` 후 모든 퀴즈 진행 + `GAME_FINISHED` 수신 후 |
| 403 | HOST 아님 또는 다른 방의 HOST | JWT role 확인, 해당 방의 hostId 일치 확인 |

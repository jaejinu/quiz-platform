package com.quiz.infra.pdf;

import com.quiz.domain.result.GameResult;
import com.quiz.domain.result.LeaderboardSnapshot;
import com.quiz.domain.result.QuizStats;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * GameResult를 HTML 문자열로 렌더링한다. 모든 사용자 입력은 escapeHtml4로 이스케이프한다.
 */
@Slf4j
@Component
public class GameResultHtmlRenderer {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(Locale.KOREA);

    private static final String HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html lang="ko"><head><meta charset="UTF-8">
        <style>
          @page { size: A4; margin: 16mm; }
          body { font-family: 'Noto Sans KR', sans-serif; color: #1d1d1f; font-size: 11pt; }
          h1 { margin: 0 0 4px; font-size: 20pt; }
          .meta { color: #86868b; font-size: 10pt; margin-bottom: 24px; }
          .code { font-family: monospace; background: #f5f5f7; padding: 2px 8px; border-radius: 4px; }
          table { width: 100%%; border-collapse: collapse; margin-bottom: 20px; }
          th, td { padding: 8px; text-align: left; border-bottom: 1px solid #e5e5ea; page-break-inside: avoid; }
          th { background: #f5f5f7; font-weight: 600; }
          .rank-1 { background: #fff3cd; }
          .rank-2 { background: #e9ecef; }
          .rank-3 { background: #fdebd0; }
          h2 { font-size: 14pt; margin: 28px 0 12px; border-bottom: 2px solid #0071e3; padding-bottom: 6px; }
          .worst-card { background: #fafafa; border-left: 4px solid #d9534f; padding: 10px 12px; margin-bottom: 8px; }
          footer { margin-top: 40px; color: #86868b; font-size: 9pt; text-align: center; border-top: 1px solid #e5e5ea; padding-top: 12px; }
        </style></head>
        <body>
          <h1>%s</h1>
          <div class="meta">
            방 코드 <span class="code">%s</span> · 호스트 %s · 시작 %s · 종료 %s · 참가자 %d명 · 퀴즈 %d개
          </div>

          <h2>최종 리더보드</h2>
          <table>
            <thead><tr><th>순위</th><th>닉네임</th><th>점수</th></tr></thead>
            <tbody>%s</tbody>
          </table>

          <h2>퀴즈별 통계</h2>
          <table>
            <thead><tr><th>#</th><th>문제</th><th>정답</th><th>정답률</th><th>평균 응답시간</th></tr></thead>
            <tbody>%s</tbody>
          </table>

          <h2>가장 많이 틀린 문제 Top 3</h2>
          %s

          <footer>
            생성 시각 %s · Quiz Platform
          </footer>
        </body></html>
        """;

    public String render(GameResult r) {
        String title = esc(r.roomTitle());
        String code = esc(r.roomCode());
        String host = esc(r.hostNickname());
        String started = formatInstant(r.startedAt());
        String finished = formatInstant(r.finishedAt());

        String leaderboardRows = renderLeaderboardRows(r.leaderboard());
        String quizRows = renderQuizRows(r.quizStats());
        String worstCards = renderWorstCards(r.worstThree());
        String now = DATE_FMT.format(LocalDateTime.now(ZONE));

        return String.format(
            HTML_TEMPLATE,
            title,
            code,
            host,
            started,
            finished,
            r.totalParticipants(),
            r.totalQuizzes(),
            leaderboardRows,
            quizRows,
            worstCards,
            now
        );
    }

    private String renderLeaderboardRows(java.util.List<LeaderboardSnapshot> leaderboard) {
        if (leaderboard.isEmpty()) {
            return "<tr><td colspan=\"3\" style=\"text-align:center;color:#86868b\">참가자가 없습니다.</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        for (LeaderboardSnapshot s : leaderboard) {
            String rowClass = switch (s.rank()) {
                case 1 -> " class=\"rank-1\"";
                case 2 -> " class=\"rank-2\"";
                case 3 -> " class=\"rank-3\"";
                default -> "";
            };
            sb.append("<tr").append(rowClass).append(">")
                .append("<td>").append(s.rank()).append("</td>")
                .append("<td>").append(esc(s.nickname())).append("</td>")
                .append("<td>").append(s.score()).append("</td>")
                .append("</tr>");
        }
        return sb.toString();
    }

    private String renderQuizRows(java.util.List<QuizStats> quizStats) {
        if (quizStats.isEmpty()) {
            return "<tr><td colspan=\"5\" style=\"text-align:center;color:#86868b\">퀴즈가 없습니다.</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        for (QuizStats q : quizStats) {
            sb.append("<tr>")
                .append("<td>").append(q.orderIndex() + 1).append("</td>")
                .append("<td>").append(esc(q.question())).append("</td>")
                .append("<td>").append(esc(q.correctAnswer())).append("</td>")
                .append("<td>").append(formatAccuracy(q.accuracyRate())).append("</td>")
                .append("<td>").append(formatMillis(q.avgResponseTimeMs())).append("</td>")
                .append("</tr>");
        }
        return sb.toString();
    }

    private String renderWorstCards(java.util.List<QuizStats> worstThree) {
        if (worstThree.isEmpty()) {
            return "<div class=\"meta\">집계할 문제가 없습니다.</div>";
        }
        StringBuilder sb = new StringBuilder();
        for (QuizStats q : worstThree) {
            sb.append("<div class=\"worst-card\">")
                .append("<strong>Q").append(q.orderIndex() + 1).append(".</strong> ")
                .append(esc(q.question()))
                .append("<div class=\"meta\">정답 ").append(esc(q.correctAnswer()))
                .append(" · 오답 ").append(q.incorrectCount()).append("건")
                .append(" · 정답률 ").append(formatAccuracy(q.accuracyRate()))
                .append("</div>")
                .append("</div>");
        }
        return sb.toString();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_FMT.format(instant.atZone(ZONE));
    }

    private String formatAccuracy(double rate) {
        return String.format(Locale.KOREA, "%.1f%%", rate * 100.0);
    }

    private String formatMillis(double ms) {
        if (ms <= 0) {
            return "-";
        }
        return String.format(Locale.KOREA, "%.0fms", ms);
    }

    private String esc(String s) {
        return StringEscapeUtils.escapeHtml4(s == null ? "" : s);
    }
}

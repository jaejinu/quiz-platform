package com.quiz.domain.game;

import com.quiz.common.exception.InvalidGameStateException;
import com.quiz.common.exception.RoomNotFoundException;
import com.quiz.common.exception.UnauthorizedException;
import com.quiz.domain.leaderboard.LeaderboardEntry;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.participant.ParticipantRepository;
import com.quiz.domain.participant.ParticipantStatus;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.infra.redis.RoomEventPublisher;
import com.quiz.infra.redis.RoomEventType;
import com.quiz.monitoring.GameAdvanceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private static final Duration LEADERBOARD_TTL_AFTER_FINISH = Duration.ofHours(1);
    private static final int FINAL_LEADERBOARD_SIZE = 100;
    private static final String INSTRUMENTATION_SCOPE = "com.quiz.game";

    private final QuizRoomRepository quizRoomRepository;
    private final QuizRepository quizRepository;
    private final GameStateStore gameStateStore;
    private final RoomEventPublisher roomEventPublisher;
    private final QuizTimerScheduler quizTimerScheduler;
    private final LeaderboardService leaderboardService;
    private final ParticipantRepository participantRepository;
    private final GameAdvanceMetrics gameAdvanceMetrics;
    private final MeterRegistry meterRegistry;
    private final OpenTelemetry openTelemetry;

    @Transactional
    public void start(Long roomId, Long hostUserId) {
        QuizRoom room = quizRoomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!room.getHostId().equals(hostUserId)) {
            throw new UnauthorizedException("호스트만 게임을 시작할 수 있습니다. roomId=" + roomId);
        }

        long quizCount = quizRepository.countByRoomId(roomId);
        if (quizCount == 0) {
            throw new InvalidGameStateException("NO_QUIZZES");
        }

        Quiz firstQuiz = quizRepository.findByRoomIdAndOrderIndex(roomId, 0)
            .orElseThrow(() -> new InvalidGameStateException("NO_QUIZZES"));

        room.start();

        long now = System.currentTimeMillis();
        int effectiveLimit = effectiveTimeLimitSec(firstQuiz, room);
        long deadline = now + effectiveLimit * 1000L;

        gameStateStore.init(roomId, firstQuiz.getId(), 0, deadline);

        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.GAME_STARTED, Map.of(
            "roomId", roomId,
            "startedAt", now
        ));
        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.QUIZ_PUSHED,
            quizPushedPayload(firstQuiz, 0, effectiveLimit, deadline));

        Long firstQuizId = firstQuiz.getId();
        quizTimerScheduler.schedule(roomId, firstQuizId, deadline, () -> advance(roomId, firstQuizId));

        log.info("game started roomId={} hostUserId={} firstQuizId={} deadline={}",
            roomId, hostUserId, firstQuizId, deadline);
    }

    /**
     * 현재 퀴즈를 종료하고 다음으로 넘어가거나 게임을 끝낸다.
     * 타이머/워치독/답변-모두-제출 등 여러 경로에서 호출되므로 Redis lock으로 싱글톤 보장.
     */
    @Transactional
    public void advance(Long roomId, Long expectedQuizId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        Span span = tracer.spanBuilder("game.advance")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("room.id", roomId != null ? roomId : -1L)
            .setAttribute("quiz.expected.id", expectedQuizId != null ? expectedQuizId : -1L)
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            if (expectedQuizId == null) {
                return;
            }
            if (!gameStateStore.tryAcquireAdvanceLock(roomId, expectedQuizId)) {
                log.debug("advance skip — lock held roomId={} quizId={}", roomId, expectedQuizId);
                return;
            }

            Long currentQuizId = gameStateStore.getCurrentQuizId(roomId);
            if (currentQuizId == null) {
                log.debug("advance skip — no active quiz roomId={}", roomId);
                return;
            }
            if (!currentQuizId.equals(expectedQuizId)) {
                log.debug("advance skip — already moved roomId={} expected={} current={}",
                    roomId, expectedQuizId, currentQuizId);
                return;
            }

            Integer currentIndex = gameStateStore.getCurrentQuizIndex(roomId);
            if (currentIndex == null) {
                log.warn("advance skip — missing index roomId={}", roomId);
                return;
            }

            int nextIndex = currentIndex + 1;
            var nextQuizOpt = quizRepository.findByRoomIdAndOrderIndex(roomId, nextIndex);

            if (nextQuizOpt.isEmpty()) {
                finish(roomId);
                return;
            }

            Quiz nextQuiz = nextQuizOpt.get();
            QuizRoom room = quizRoomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
            int effectiveLimit = effectiveTimeLimitSec(nextQuiz, room);
            long now = System.currentTimeMillis();
            long deadline = now + effectiveLimit * 1000L;

            gameStateStore.advance(roomId, nextQuiz.getId(), nextIndex, deadline);

            roomEventPublisher.publishAfterCommit(roomId, RoomEventType.QUIZ_PUSHED,
                quizPushedPayload(nextQuiz, nextIndex, effectiveLimit, deadline));

            Long nextQuizId = nextQuiz.getId();
            quizTimerScheduler.schedule(roomId, nextQuizId, deadline, () -> advance(roomId, nextQuizId));

            span.setAttribute("quiz.next.id", nextQuizId);
            span.setAttribute("quiz.next.index", nextIndex);

            log.info("advance roomId={} from quizId={} to quizId={} index={} deadline={}",
                roomId, expectedQuizId, nextQuizId, nextIndex, deadline);
        } catch (RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
            sample.stop(gameAdvanceMetrics.advanceTimer());
        }
    }

    @Transactional
    public void finish(Long roomId) {
        QuizRoom room = quizRoomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        room.finish();

        List<LeaderboardEntry> finalBoard = leaderboardService.top(roomId, FINAL_LEADERBOARD_SIZE);

        roomEventPublisher.publishAfterCommit(roomId, RoomEventType.GAME_FINISHED, Map.of(
            "roomId", roomId,
            "leaderboard", finalBoard
        ));

        leaderboardService.finalizeAndExpire(roomId, LEADERBOARD_TTL_AFTER_FINISH);
        gameStateStore.clear(roomId);

        log.info("game finished roomId={} leaderboardSize={}", roomId, finalBoard.size());
    }

    /**
     * D 에이전트가 매 답변 처리 후 호출.
     * 현재 퀴즈에 연결된 참가자 전원이 답변을 제출했으면 조기 advance.
     *
     * @Transactional 명시: self-invocation으로 advance()의 프록시가 우회되어도,
     * outer 트랜잭션이 살아 있도록 진입점 자체를 트랜잭션화.
     */
    @Transactional
    public void onAnswerCounted(Long roomId, Long quizId) {
        Long currentQuizId = gameStateStore.getCurrentQuizId(roomId);
        if (currentQuizId == null || !currentQuizId.equals(quizId)) {
            return;
        }
        long connected = participantRepository.countByRoomIdAndStatus(roomId, ParticipantStatus.CONNECTED);
        if (connected <= 0) {
            return;
        }
        long answers = gameStateStore.getAnswerCount(roomId, quizId);
        if (answers >= connected) {
            log.debug("all answered — early advance roomId={} quizId={} answers={} connected={}",
                roomId, quizId, answers, connected);
            advance(roomId, quizId);
        }
    }

    private static int effectiveTimeLimitSec(Quiz quiz, QuizRoom room) {
        int t = quiz.getTimeLimit();
        return t > 0 ? t : room.getDefaultTimeLimit();
    }

    private static Map<String, Object> quizPushedPayload(Quiz quiz, int orderIndex, int timeLimitSec, long deadline) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("quizId", quiz.getId());
        payload.put("orderIndex", orderIndex);
        payload.put("question", quiz.getQuestion());
        payload.put("type", quiz.getType());
        payload.put("options", quiz.getOptions());
        payload.put("timeLimit", timeLimitSec);
        payload.put("deadline", deadline);
        payload.put("imageUrl", quiz.getImageUrl());
        return payload;
    }
}

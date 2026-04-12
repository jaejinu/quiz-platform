package com.quiz.integration;

import com.quiz.domain.game.GameService;
import com.quiz.domain.game.GameStateKeys;
import com.quiz.domain.game.GameStateStore;
import com.quiz.domain.game.QuizTimerScheduler;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.participant.Participant;
import com.quiz.domain.participant.ParticipantRepository;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.domain.user.User;
import com.quiz.infra.redis.RoomEventPublisher;
import com.quiz.monitoring.GameAdvanceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * advance 싱글톤 보장 테스트.
 *
 * 한계: 단일 JVM에서 {@link GameStateStore}를 두 개 수동 인스턴스화해 서로 다른
 * {@code publisherId}를 부여한다. 진짜 두 노드의 경쟁은 아니지만 Redis SET NX PX
 * 로직이 동시 호출에서 정확히 한 쪽만 승리하는지 검증한다.
 */
class DistributedAdvanceLockTest extends AbstractIntegrationTest {

    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private QuizRoomRepository quizRoomRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private RoomEventPublisher roomEventPublisher;
    @Autowired private QuizTimerScheduler quizTimerScheduler;
    @Autowired private LeaderboardService leaderboardService;
    @Autowired private GameAdvanceMetrics gameAdvanceMetrics;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void onlyOneInstanceWinsAdvance() throws Exception {
        User host = factory.createHost();
        QuizRoom room = factory.createRoom(host.getId(), 10, 30);
        Long roomId = room.getId();

        Quiz q1 = factory.addQuiz(roomId, 0, "Q1", "A", 30);
        Quiz q2 = factory.addQuiz(roomId, 1, "Q2", "B", 30);

        // 참가자 1명 — advance 내부 로직이 participant count 이용할 수 있어서 안전차원.
        Participant p = participantRepository.save(Participant.builder()
                .roomId(roomId)
                .userId(host.getId())
                .sessionId("seed-session")
                .build());

        // 두 인스턴스(pub-A, pub-B). GameService는 publisherId를 직접 쓰지 않지만
        // GameStateStore.tryAcquireAdvanceLock 이 publisherId를 lock value로 세팅한다.
        GameStateStore storeA = new GameStateStore(stringRedisTemplate, redisTemplate, "pub-A");
        GameStateStore storeB = new GameStateStore(stringRedisTemplate, redisTemplate, "pub-B");

        GameService gsA = new GameService(quizRoomRepository, quizRepository, storeA,
                roomEventPublisher, quizTimerScheduler, leaderboardService,
                participantRepository, gameAdvanceMetrics, meterRegistry);
        GameService gsB = new GameService(quizRoomRepository, quizRepository, storeB,
                roomEventPublisher, quizTimerScheduler, leaderboardService,
                participantRepository, gameAdvanceMetrics, meterRegistry);

        // start는 한 쪽만. 상태가 공유 Redis에 기록되므로 둘 다 읽음.
        gsA.start(roomId, host.getId());

        Long firstQuizId = storeA.getCurrentQuizId(roomId);
        assertThat(firstQuizId).isEqualTo(q1.getId());

        // --- 동시 advance 호출 ---
        ExecutorService ex = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> { gsA.advance(roomId, firstQuizId); return true; },
                    () -> { gsB.advance(roomId, firstQuizId); return true; }
            );
            List<Future<Boolean>> futures = new ArrayList<>(ex.invokeAll(tasks));
            for (Future<Boolean> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            ex.shutdownNow();
        }

        // 락 값 확인 — A 또는 B 중 한쪽의 publisherId 만 존재해야 한다.
        String lockOwner = stringRedisTemplate.opsForValue()
                .get(GameStateKeys.advanceLock(roomId, firstQuizId));
        assertThat(lockOwner).isIn("pub-A", "pub-B");

        // currentQuiz가 딱 한 번만 전이됐는지: q1 → q2.
        Long afterQuizId = storeA.getCurrentQuizId(roomId);
        assertThat(afterQuizId).isEqualTo(q2.getId());

        // 인덱스도 1로 한 번만 증가 (중복 advance였다면 2가 됐을 것).
        Integer afterIndex = storeA.getCurrentQuizIndex(roomId);
        assertThat(afterIndex).isEqualTo(1);

        // 동일 quizId로 다시 호출해도 이미 전이됐으므로 no-op.
        gsA.advance(roomId, firstQuizId);
        gsB.advance(roomId, firstQuizId);
        assertThat(storeA.getCurrentQuizId(roomId)).isEqualTo(q2.getId());
        assertThat(storeA.getCurrentQuizIndex(roomId)).isEqualTo(1);

        // cleanup
        participantRepository.delete(p);
    }
}

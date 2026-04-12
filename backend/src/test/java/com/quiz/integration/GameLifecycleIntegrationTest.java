package com.quiz.integration;

import com.quiz.domain.answer.AnswerRepository;
import com.quiz.domain.leaderboard.LeaderboardEntry;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.domain.room.RoomStatus;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: 방 생성 → 3명 join → 퀴즈 3개 진행 → GAME_FINISHED 수신.
 *
 * 퀴즈 timeLimit=2초. 전원이 빠르게 답하면 onAnswerCounted로 조기 advance.
 * 타이머 만료를 기다리지 않도록 세션별로 답을 쏜다.
 */
class GameLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private LeaderboardService leaderboardService;
    @Autowired
    private QuizRoomRepository quizRoomRepository;

    @Test
    void fullGameLifecycle() throws Exception {
        // --- setup ---
        User host = factory.createHost();
        User p1 = factory.createPlayer("alice");
        User p2 = factory.createPlayer("bob");
        User p3 = factory.createPlayer("carol");

        QuizRoom room = factory.createRoom(host.getId(), 10, 30);
        Long roomId = room.getId();

        Quiz q1 = factory.addQuiz(roomId, 0, "Q1", "A", 2);
        Quiz q2 = factory.addQuiz(roomId, 1, "Q2", "B", 2);
        Quiz q3 = factory.addQuiz(roomId, 2, "Q3", "C", 2);

        // --- connect ---
        StompSession hostSession = stompClient.connect(port, host.getId(), host.getNickname(), "HOST");
        StompSession s1 = stompClient.connect(port, p1.getId(), p1.getNickname(), "PLAYER");
        StompSession s2 = stompClient.connect(port, p2.getId(), p2.getNickname(), "PLAYER");
        StompSession s3 = stompClient.connect(port, p3.getId(), p3.getNickname(), "PLAYER");

        String topic = "/topic/room/" + roomId;
        BlockingQueue<Map> hostTopic = stompClient.subscribe(hostSession, topic, Map.class);
        BlockingQueue<Map> t1 = stompClient.subscribe(s1, topic, Map.class);
        stompClient.subscribe(s2, topic, Map.class);
        stompClient.subscribe(s3, topic, Map.class);

        // 플레이어 3명 join
        stompClient.send(s1, "/app/room/" + roomId + "/join", new byte[0]);
        stompClient.send(s2, "/app/room/" + roomId + "/join", new byte[0]);
        stompClient.send(s3, "/app/room/" + roomId + "/join", new byte[0]);

        // PLAYER_JOINED 3개 수신
        assertThat(pollType(hostTopic, "PLAYER_JOINED", 5)).isNotNull();
        assertThat(pollType(hostTopic, "PLAYER_JOINED", 5)).isNotNull();
        assertThat(pollType(hostTopic, "PLAYER_JOINED", 5)).isNotNull();

        // --- start ---
        stompClient.send(hostSession, "/app/room/" + roomId + "/start", new byte[0]);

        assertThat(pollType(hostTopic, "GAME_STARTED", 5)).isNotNull();
        Map firstQuiz = pollType(hostTopic, "QUIZ_PUSHED", 5);
        assertThat(firstQuiz).isNotNull();

        // --- Q1: 답 제출 (p1=A 정답, p2=B 오답, p3=A 정답) ---
        submitAnswer(s1, roomId, q1.getId(), "A");
        submitAnswer(s2, roomId, q1.getId(), "B");
        submitAnswer(s3, roomId, q1.getId(), "A");

        // 전원 제출 → 조기 advance → QUIZ_PUSHED
        assertThat(pollType(hostTopic, "QUIZ_PUSHED", 10)).isNotNull();

        // --- Q2: p1=B 정답, p2=A 오답, p3=B 정답 ---
        submitAnswer(s1, roomId, q2.getId(), "B");
        submitAnswer(s2, roomId, q2.getId(), "A");
        submitAnswer(s3, roomId, q2.getId(), "B");

        assertThat(pollType(hostTopic, "QUIZ_PUSHED", 10)).isNotNull();

        // --- Q3: p1=C 정답, p2=D 오답, p3=C 정답 ---
        submitAnswer(s1, roomId, q3.getId(), "C");
        submitAnswer(s2, roomId, q3.getId(), "D");
        submitAnswer(s3, roomId, q3.getId(), "C");

        // GAME_FINISHED
        Map finished = pollType(hostTopic, "GAME_FINISHED", 10);
        assertThat(finished).isNotNull();

        // --- 어설션 ---
        // 9개 answer (3명 × 3퀴즈)
        assertThat(answerRepository.count()).isEqualTo(9L);

        // 방 상태
        QuizRoom persisted = quizRoomRepository.findById(roomId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RoomStatus.FINISHED);

        // 리더보드 — p1/p3는 3정답, p2는 0정답. (p2는 Redis ZSet에 entry 없을 수 있음)
        List<LeaderboardEntry> top = leaderboardService.top(roomId, 10);
        assertThat(top).isNotEmpty();
        List<Long> topUserIds = top.stream().map(LeaderboardEntry::userId).toList();
        assertThat(topUserIds).containsAnyOf(p1.getId(), p3.getId());
        // p2는 한 문제도 못 맞춰서 ZSet에 존재하지 않거나 0점으로 꼴찌여야 함
        if (topUserIds.contains(p2.getId())) {
            int p2Score = top.stream().filter(e -> e.userId().equals(p2.getId())).findFirst().orElseThrow().score();
            assertThat(p2Score).isEqualTo(0);
        }

        // cleanup
        hostSession.disconnect();
        s1.disconnect();
        s2.disconnect();
        s3.disconnect();
    }

    private void submitAnswer(StompSession session, Long roomId, Long quizId, String answer) {
        stompClient.send(session, "/app/room/" + roomId + "/answer",
                Map.of("quizId", quizId, "answer", answer));
    }

    /**
     * 큐에서 특정 event type 이 나올 때까지 최대 timeoutSec 대기. 다른 이벤트는 drain.
     */
    @SuppressWarnings("rawtypes")
    private Map pollType(BlockingQueue<Map> queue, String type, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            long remain = deadline - System.currentTimeMillis();
            Map evt = queue.poll(remain, TimeUnit.MILLISECONDS);
            if (evt == null) {
                return null;
            }
            if (type.equals(String.valueOf(evt.get("type")))) {
                return evt;
            }
        }
        return null;
    }
}

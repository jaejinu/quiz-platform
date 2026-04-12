package com.quiz.integration;

import com.quiz.domain.answer.AnswerRepository;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimerExpirationTest extends AbstractIntegrationTest {

    @Autowired
    AnswerRepository answerRepository;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void quizTimeout_withoutAnswers_advancesToNextQuizViaWatchdog() throws Exception {
        User host = factory.createHost();
        User player = factory.createPlayer("p1");
        QuizRoom room = factory.createRoom(host.getId(), 10, 2);
        Long roomId = room.getId();

        Quiz q1 = factory.addQuiz(roomId, 0, "Q1?", "A", 2);
        Quiz q2 = factory.addQuiz(roomId, 1, "Q2?", "B", 2);

        StompSession hostSession = stompClient.connect(port, host.getId(), "host", "HOST");
        StompSession playerSession = stompClient.connect(port, player.getId(), "p1", "PLAYER");

        BlockingQueue<Map> playerQueue = stompClient.subscribe(playerSession, "/topic/room/" + roomId, Map.class);

        stompClient.send(playerSession, "/app/room/" + roomId + "/join", Map.of());
        Thread.sleep(500);

        stompClient.send(hostSession, "/app/room/" + roomId + "/start", Map.of());

        Map firstPush = pollForType(playerQueue, "QUIZ_PUSHED", 5);
        assertThat(firstPush).as("first QUIZ_PUSHED").isNotNull();
        Map firstPayload = (Map) firstPush.get("payload");
        assertThat(toLong(firstPayload.get("quizId"))).isEqualTo(q1.getId());

        Map secondPush = pollForType(playerQueue, "QUIZ_PUSHED", 10);
        assertThat(secondPush).as("second QUIZ_PUSHED after timer expiration").isNotNull();
        Map secondPayload = (Map) secondPush.get("payload");
        assertThat(toLong(secondPayload.get("quizId"))).isEqualTo(q2.getId());

        assertThat(answerRepository.count()).isZero();
    }

    @SuppressWarnings("rawtypes")
    private Map pollForType(BlockingQueue<Map> queue, String type, long seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            long remaining = Math.max(100, deadline - System.currentTimeMillis());
            Map msg = queue.poll(remaining, TimeUnit.MILLISECONDS);
            if (msg == null) {
                return null;
            }
            if (type.equals(msg.get("type"))) {
                return msg;
            }
        }
        return null;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}

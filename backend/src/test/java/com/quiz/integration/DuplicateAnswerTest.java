package com.quiz.integration;

import com.quiz.domain.answer.AnswerRepository;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.user.User;
import com.quiz.monitoring.AnswerMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 quizId로 두 번 답변 → DB에는 1건만 저장되고 AnswerProcessor는 duplicate를 카운트한다.
 * DLQ에는 들어가지 않아야 한다 (AnswerProcessor가 예외 없이 return).
 */
class DuplicateAnswerTest extends AbstractIntegrationTest {

    @Autowired private AnswerRepository answerRepository;
    @Autowired private AnswerMetrics answerMetrics;

    @Test
    void duplicateAnswerIsSkippedNotDlqed() throws Exception {
        User host = factory.createHost();
        User player = factory.createPlayer("alice");
        QuizRoom room = factory.createRoom(host.getId(), 10, 30);
        Long roomId = room.getId();
        Quiz q1 = factory.addQuiz(roomId, 0, "Q1", "A", 30);

        StompSession hostSession = stompClient.connect(port, host.getId(), host.getNickname(), "HOST");
        StompSession playerSession = stompClient.connect(port, player.getId(), player.getNickname(), "PLAYER");

        String topic = "/topic/room/" + roomId;
        BlockingQueue<Map> hostTopic = stompClient.subscribe(hostSession, topic, Map.class);
        BlockingQueue<Map> playerTopic = stompClient.subscribe(playerSession, topic, Map.class);

        // 플레이어 입장 + 게임 시작
        stompClient.send(playerSession, "/app/room/" + roomId + "/join", new byte[0]);
        assertThat(pollType(hostTopic, "PLAYER_JOINED", 5)).isNotNull();

        double duplicateBefore = answerMetrics.counterProcessed("duplicate").count();

        stompClient.send(hostSession, "/app/room/" + roomId + "/start", new byte[0]);
        assertThat(pollType(playerTopic, "QUIZ_PUSHED", 5)).isNotNull();

        // 같은 퀴즈에 두 번 답변
        Map<String, Object> payload = Map.of("quizId", q1.getId(), "answer", "A");
        stompClient.send(playerSession, "/app/room/" + roomId + "/answer", payload);
        Thread.sleep(200); // 첫 답변이 DB에 들어갈 여유
        stompClient.send(playerSession, "/app/room/" + roomId + "/answer", payload);

        // RabbitMQ 처리 대기
        Thread.sleep(2000);

        // Answer는 정확히 1건만 저장
        assertThat(answerRepository.count()).isEqualTo(1L);

        // duplicate counter가 최소 1 증가 (DB 사전 check 또는 unique 위반 경로 중 하나)
        double duplicateAfter = answerMetrics.counterProcessed("duplicate").count();
        assertThat(duplicateAfter - duplicateBefore).isGreaterThanOrEqualTo(1.0);

        // error counter는 증가하지 않아야 한다 (DLQ 경로 아님)
        assertThat(answerMetrics.counterProcessed("error").count()).isEqualTo(0.0);

        hostSession.disconnect();
        playerSession.disconnect();
    }

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

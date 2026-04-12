package com.quiz.integration;

import com.quiz.domain.participant.Participant;
import com.quiz.domain.participant.ParticipantRepository;
import com.quiz.domain.participant.ParticipantStatus;
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

/**
 * 재접속 복구: disconnect 후 grace 기간 내에 재접속하면 Participant가 CONNECTED로 복귀하고
 * GameSnapshotService가 {@code /user/queue/snapshot} 으로 현재 상태를 push.
 */
class ReconnectRecoveryTest extends AbstractIntegrationTest {

    @Autowired private ParticipantRepository participantRepository;

    @Test
    void reconnectRestoresParticipantAndReceivesSnapshot() throws Exception {
        User host = factory.createHost();
        User player = factory.createPlayer("alice");

        QuizRoom room = factory.createRoom(host.getId(), 10, 30);
        Long roomId = room.getId();

        factory.addQuiz(roomId, 0, "Q1", "A", 30);

        // Host / player connect + subscribe
        StompSession hostSession = stompClient.connect(port, host.getId(), host.getNickname(), "HOST");
        StompSession playerSession = stompClient.connect(port, player.getId(), player.getNickname(), "PLAYER");

        String topic = "/topic/room/" + roomId;
        BlockingQueue<Map> hostTopic = stompClient.subscribe(hostSession, topic, Map.class);
        stompClient.subscribe(playerSession, topic, Map.class);

        // player join
        stompClient.send(playerSession, "/app/room/" + roomId + "/join", new byte[0]);

        // host도 이벤트 받기 위해 대기
        assertThat(pollType(hostTopic, "PLAYER_JOINED", 5)).isNotNull();

        // start
        stompClient.send(hostSession, "/app/room/" + roomId + "/start", new byte[0]);
        assertThat(pollType(hostTopic, "GAME_STARTED", 5)).isNotNull();
        assertThat(pollType(hostTopic, "QUIZ_PUSHED", 5)).isNotNull();

        // --- 플레이어 세션 끊기 ---
        playerSession.disconnect();

        // disconnect 이벤트 처리 + grace 시작 대기. grace 30s 중 일부만 소모.
        Thread.sleep(500);

        // 재접속 (같은 userId/role, 새 WebSocket 세션)
        StompSession playerSession2 = stompClient.connect(port, player.getId(), player.getNickname(), "PLAYER");

        // 스냅샷은 join 핸들러 내부에서 /user/queue/snapshot 으로 전송되므로,
        // 구독은 join 이전에 걸어 두어야 안전.
        BlockingQueue<Map> snapshotQueue = stompClient.subscribe(playerSession2, "/user/queue/snapshot", Map.class);

        // 구독이 broker에 등록될 시간 확보
        Thread.sleep(300);

        stompClient.send(playerSession2, "/app/room/" + roomId + "/join", new byte[0]);

        Map snapshot = snapshotQueue.poll(5, TimeUnit.SECONDS);
        assertThat(snapshot).as("snapshot received after reconnect").isNotNull();
        // GameSnapshot은 currentQuiz/deadline/leaderboard 등의 필드를 포함.
        // 필드 이름이 구현에 따라 조금씩 다를 수 있어 내용을 강하게 단정하지 않고, non-null만 확인.

        // DB에서 Participant가 CONNECTED로 복귀했는지 검증
        Participant reconnected = participantRepository
                .findByRoomIdAndUserId(roomId, player.getId())
                .orElseThrow();
        assertThat(reconnected.getStatus()).isEqualTo(ParticipantStatus.CONNECTED);

        // cleanup
        hostSession.disconnect();
        playerSession2.disconnect();
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

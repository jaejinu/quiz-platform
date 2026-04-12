package com.quiz.integration;

import com.quiz.domain.leaderboard.LeaderboardKeys;
import com.quiz.infra.redis.RoomEvent;
import com.quiz.infra.redis.RoomEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PubSubSelfSkipTest extends AbstractIntegrationTest {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("publisherId")
    String publisherId;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void selfPublishedEvent_isSkipped_butForeignEvent_isDelivered() throws Exception {
        Long hostId = factory.createHost().getId();
        Long roomId = factory.createRoom(hostId, 10, 30).getId();

        StompSession host = stompClient.connect(port, hostId, "host", "HOST");
        BlockingQueue<Map> queue = stompClient.subscribe(host, "/topic/room/" + roomId, Map.class);

        Thread.sleep(300);

        RoomEvent selfEvent = RoomEvent.of(
                RoomEventType.PLAYER_JOINED,
                roomId,
                publisherId,
                Map.of("test", 1)
        );
        redisTemplate.convertAndSend(LeaderboardKeys.roomChannel(roomId), selfEvent);

        Thread.sleep(1000);

        Map skipped = queue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(skipped).as("self-published event must be skipped by RoomEventSubscriber").isNull();

        RoomEvent foreignEvent = RoomEvent.of(
                RoomEventType.PLAYER_JOINED,
                roomId,
                "other-server",
                Map.of("test", 2)
        );
        redisTemplate.convertAndSend(LeaderboardKeys.roomChannel(roomId), foreignEvent);

        Map received = queue.poll(5, TimeUnit.SECONDS);
        assertThat(received).as("foreign-published event must be delivered via the Redis bridge").isNotNull();
        assertThat(received.get("type")).isEqualTo("PLAYER_JOINED");
        assertThat(received.get("publisherId")).isEqualTo("other-server");
    }
}

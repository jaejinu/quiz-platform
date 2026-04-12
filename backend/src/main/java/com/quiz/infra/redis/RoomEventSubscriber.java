package com.quiz.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventSubscriber implements MessageListener {

    private static final String TOPIC_PREFIX = "/topic/room/";

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("publisherId")
    private final String publisherId;
    private final EventMetrics eventMetrics;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        RoomEvent event;
        try {
            event = objectMapper.readValue(body, RoomEvent.class);
        } catch (Exception e) {
            log.error("failed to deserialize RoomEvent, dropping message: body={} err={}", body, e.getMessage(), e);
            return;
        }

        if (publisherId.equals(event.publisherId())) {
            log.trace("self-skip roomId={} type={}", event.roomId(), event.type());
            return;
        }

        eventMetrics.incrementReceived(event.type());
        if (event.timestamp() != null) {
            eventMetrics.recordPubSubLatency(Duration.between(event.timestamp(), Instant.now()));
        }

        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + event.roomId(), event);
    }
}

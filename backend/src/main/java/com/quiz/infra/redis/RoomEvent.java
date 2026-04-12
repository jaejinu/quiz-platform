package com.quiz.infra.redis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomEvent(
        RoomEventType type,
        Long roomId,
        String publisherId,
        Instant timestamp,
        Map<String, Object> payload
) {

    @JsonCreator
    public RoomEvent(
            @JsonProperty("type") RoomEventType type,
            @JsonProperty("roomId") Long roomId,
            @JsonProperty("publisherId") String publisherId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("payload") Map<String, Object> payload
    ) {
        this.type = type;
        this.roomId = roomId;
        this.publisherId = publisherId;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public static RoomEvent of(RoomEventType type, Long roomId, String publisherId, Map<String, Object> payload) {
        return new RoomEvent(type, roomId, publisherId, Instant.now(), payload);
    }
}

package com.quiz.infra.redis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Redis Pub/Sub 및 STOMP broadcast로 흘려보내는 방 단위 이벤트.
 *
 * <p>{@code traceparent}는 W3C Trace Context 헤더 문자열로, 발행 시점의 현재 span을 원격 서버
 * 구독자가 복원할 수 있도록 동반한다. Nullable — tracing 비활성 환경 또는 span 밖에서 발행된
 * 메시지에선 null일 수 있다. {@link JsonIgnoreProperties#ignoreUnknown()}와 맞물려 필드 추가는
 * 하위호환: 구버전 인스턴스가 신버전 페이로드를 받아도 무시하고 역직렬화한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomEvent(
        RoomEventType type,
        Long roomId,
        String publisherId,
        Instant timestamp,
        Map<String, Object> payload,
        String traceparent
) {

    @JsonCreator
    public RoomEvent(
            @JsonProperty("type") RoomEventType type,
            @JsonProperty("roomId") Long roomId,
            @JsonProperty("publisherId") String publisherId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("traceparent") String traceparent
    ) {
        this.type = type;
        this.roomId = roomId;
        this.publisherId = publisherId;
        this.timestamp = timestamp;
        this.payload = payload;
        this.traceparent = traceparent;
    }

    public static RoomEvent of(RoomEventType type, Long roomId, String publisherId, Map<String, Object> payload) {
        return new RoomEvent(type, roomId, publisherId, Instant.now(), payload, null);
    }

    public static RoomEvent of(RoomEventType type, Long roomId, String publisherId,
                               Map<String, Object> payload, String traceparent) {
        return new RoomEvent(type, roomId, publisherId, Instant.now(), payload, traceparent);
    }
}

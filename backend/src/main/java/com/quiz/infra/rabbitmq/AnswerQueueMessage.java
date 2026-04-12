package com.quiz.infra.rabbitmq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 답변 큐 페이로드. Jackson2JsonMessageConverter로 직렬화/역직렬화된다.
 *
 * publisherId는 idempotency 로그/관측 목적 (어느 인스턴스가 enqueue했는지 추적).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerQueueMessage(
        Long roomId,
        Long participantId,
        Long userId,
        String nickname,
        Long quizId,
        String answer,
        long responseTimeMs,
        Instant submittedAt,
        String publisherId
) {

    @JsonCreator
    public AnswerQueueMessage(
            @JsonProperty("roomId") Long roomId,
            @JsonProperty("participantId") Long participantId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("quizId") Long quizId,
            @JsonProperty("answer") String answer,
            @JsonProperty("responseTimeMs") long responseTimeMs,
            @JsonProperty("submittedAt") Instant submittedAt,
            @JsonProperty("publisherId") String publisherId
    ) {
        this.roomId = roomId;
        this.participantId = participantId;
        this.userId = userId;
        this.nickname = nickname;
        this.quizId = quizId;
        this.answer = answer;
        this.responseTimeMs = responseTimeMs;
        this.submittedAt = submittedAt;
        this.publisherId = publisherId;
    }
}

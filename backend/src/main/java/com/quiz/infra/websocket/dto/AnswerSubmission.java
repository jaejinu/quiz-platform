package com.quiz.infra.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerSubmission(Long quizId, String answer) {
}

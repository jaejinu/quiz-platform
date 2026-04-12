package com.quiz.api.dto;

import java.util.List;

/**
 * 퀴즈 응답. correctAnswer 필드는 HOST 조회 시에만 세팅되고 PLAYER 노출 시에는 null.
 */
public record QuizResponse(
    Long id,
    int orderIndex,
    String question,
    String type,
    List<String> options,
    int timeLimit,
    String correctAnswer,
    String imageUrl
) {
}

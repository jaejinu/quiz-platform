package com.quiz.api.dto;

import com.quiz.domain.quiz.QuizType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateQuizRequest(
    @NotBlank @Size(max = 500) String question,
    @NotNull QuizType type,
    List<String> options,
    @NotBlank String correctAnswer,
    Integer timeLimit,
    String imageUrl
) {
}

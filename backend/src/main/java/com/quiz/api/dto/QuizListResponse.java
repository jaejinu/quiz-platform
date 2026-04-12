package com.quiz.api.dto;

import java.util.List;

public record QuizListResponse(List<QuizResponse> quizzes) {
}

package com.quiz.api.dto;

public record RoomResponse(
    Long id,
    String code,
    String title,
    String status,
    Long hostId,
    int maxPlayers,
    int defaultTimeLimit,
    int participantCount
) {
}

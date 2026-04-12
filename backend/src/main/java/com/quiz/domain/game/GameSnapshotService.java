package com.quiz.domain.game;

import com.quiz.api.dto.QuizResponse;
import com.quiz.domain.leaderboard.LeaderboardEntry;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.quiz.QuizService;
import com.quiz.domain.room.RoomService;
import com.quiz.infra.websocket.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSnapshotService {

    private final GameStateStore gameStateStore;
    private final QuizRepository quizRepository;
    private final QuizService quizService;
    private final LeaderboardService leaderboardService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final RoomService roomService;

    public void sendSnapshot(AuthPrincipal principal, Long roomId) {
        GameStateSnapshot state = gameStateStore.snapshot(roomId);
        String status = roomService.getEntityOrThrow(roomId).getStatus().name();

        QuizResponse currentQuiz = null;
        long remainingMs = 0L;
        if (state != null && state.currentQuizId() != null) {
            currentQuiz = quizService.getForPlayer(state.currentQuizId());
            if (state.deadlineMs() != null) {
                remainingMs = Math.max(0L, state.deadlineMs() - System.currentTimeMillis());
            }
        }

        List<LeaderboardEntry> top = leaderboardService.top(roomId, 10);

        GameSnapshot snapshot = new GameSnapshot(roomId, status, currentQuiz, remainingMs, top);
        simpMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/snapshot", snapshot);
        log.debug("snapshot sent to user={} roomId={} status={} remainingMs={}",
                principal.getName(), roomId, status, remainingMs);
    }
}

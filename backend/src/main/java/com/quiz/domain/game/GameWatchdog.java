package com.quiz.domain.game;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 1초 주기로 만료된 퀴즈를 점검하는 안전망.
 * 로컬 타이머가 유실/지연되거나 deadline 지난 뒤 노드가 살아나도 진행이 계속되도록.
 * advance 싱글톤은 Redis lock에 의해 보장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWatchdog {

    private final GameStateStore gameStateStore;
    private final GameService gameService;

    @Scheduled(fixedRate = 1000L)
    public void sweep() {
        Set<Long> activeRooms = gameStateStore.getActiveRoomIds();
        if (activeRooms.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Long roomId : activeRooms) {
            try {
                Long deadline = gameStateStore.getDeadline(roomId);
                if (deadline == null || deadline > now) {
                    continue;
                }
                Long currentQuizId = gameStateStore.getCurrentQuizId(roomId);
                if (currentQuizId == null) {
                    continue;
                }
                log.debug("watchdog firing advance roomId={} quizId={} overdueMs={}",
                    roomId, currentQuizId, now - deadline);
                gameService.advance(roomId, currentQuizId);
            } catch (Exception e) {
                log.warn("watchdog sweep failed roomId={}: {}", roomId, e.getMessage(), e);
            }
        }
    }
}

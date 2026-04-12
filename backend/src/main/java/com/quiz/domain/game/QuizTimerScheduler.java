package com.quiz.domain.game;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 로컬 노드 전용 타이머. deadline 만료 시 onExpire Runnable을 실행한다.
 * 분산 싱글톤은 GameService.advance 내부의 Redis lock에 위임 — 모든 노드가 각자 타이머를 돌려도 OK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizTimerScheduler {

    private final ScheduledExecutorService gameScheduler;

    public void schedule(Long roomId, Long quizId, long deadlineMs, Runnable onExpire) {
        long delayMs = deadlineMs - System.currentTimeMillis();
        long clamped = Math.max(0L, delayMs);
        log.debug("schedule expire roomId={} quizId={} delayMs={}", roomId, quizId, clamped);
        gameScheduler.schedule(() -> {
            try {
                onExpire.run();
            } catch (Exception e) {
                log.warn("quiz timer onExpire failed roomId={} quizId={}: {}",
                    roomId, quizId, e.getMessage(), e);
            }
        }, clamped, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        log.info("QuizTimerScheduler shutdown — awaiting termination");
        gameScheduler.shutdown();
        try {
            if (!gameScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                gameScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            gameScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

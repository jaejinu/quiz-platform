package com.quiz.domain.answer;

import com.quiz.domain.game.GameStateStore;
import com.quiz.domain.participant.Participant;
import com.quiz.domain.participant.ParticipantService;
import com.quiz.infra.rabbitmq.AnswerQueueMessage;
import com.quiz.infra.rabbitmq.RabbitConfig;
import com.quiz.infra.websocket.AuthPrincipal;
import com.quiz.infra.websocket.dto.AnswerSubmission;
import com.quiz.monitoring.AnswerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 답변 제출을 받아 RabbitMQ 큐에 enqueue한다.
 *
 * 동기 처리 금지: 정답 판정/점수 계산/리더보드/이벤트는 AnswerProcessor가 담당.
 * 여기서는 (1) stale answer drop (2) participant lookup (3) responseTime 계산 (4) enqueue만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerService {

    private final RabbitTemplate rabbitTemplate;
    private final ParticipantService participantService;
    private final GameStateStore gameStateStore;
    @Qualifier("publisherId")
    private final String publisherId;
    private final AnswerMetrics answerMetrics;

    public void accept(Long roomId, AuthPrincipal principal, AnswerSubmission submission) {
        log.debug("answer accept roomId={} userId={} quizId={}", roomId, principal.userId(), submission.quizId());

        Long currentQuizId = gameStateStore.getCurrentQuizId(roomId);
        if (currentQuizId == null || !currentQuizId.equals(submission.quizId())) {
            // 클라이언트 타이밍 노이즈(퀴즈가 이미 넘어간 뒤 도착). 예외 X.
            log.info("stale answer dropped roomId={} userId={} submitted={} current={}",
                    roomId, principal.userId(), submission.quizId(), currentQuizId);
            return;
        }

        Long quizStartedAtMs = gameStateStore.getQuizStartedAt(roomId);
        if (quizStartedAtMs == null) {
            log.info("quizStartedAt missing, drop answer roomId={} userId={} quizId={}",
                    roomId, principal.userId(), submission.quizId());
            return;
        }

        Participant participant = participantService.getByRoomAndUserOrThrow(roomId, principal.userId());

        long responseTimeMs = System.currentTimeMillis() - quizStartedAtMs;
        if (responseTimeMs < 0) {
            responseTimeMs = 0L;
        }

        AnswerQueueMessage msg = new AnswerQueueMessage(
                roomId,
                participant.getId(),
                principal.userId(),
                principal.nickname(),
                submission.quizId(),
                submission.answer(),
                responseTimeMs,
                Instant.now(),
                publisherId
        );

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, msg);
        answerMetrics.counterEnqueued().increment();
        log.debug("answer enqueued roomId={} userId={} quizId={} responseTimeMs={}",
                roomId, principal.userId(), submission.quizId(), responseTimeMs);
    }
}

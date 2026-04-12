package com.quiz.domain.answer;

import com.quiz.common.exception.QuizNotFoundException;
import com.quiz.domain.game.GameService;
import com.quiz.domain.game.GameStateStore;
import com.quiz.domain.leaderboard.LeaderboardEntry;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.infra.rabbitmq.AnswerQueueMessage;
import com.quiz.infra.rabbitmq.RabbitConfig;
import com.quiz.infra.redis.RoomEventPublisher;
import com.quiz.infra.redis.RoomEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 큐에 쌓인 답변을 워커로 처리.
 *
 * 흐름:
 * 1) 중복 체크 (uk_answers_participant_quiz 선제 방어)
 * 2) Quiz 로드 → 정답 판정 → 점수 계산
 * 3) Answer 저장 (unique 제약 위반 시 ack drop)
 * 4) 정답이면 리더보드 increment
 * 5) answered count++ → ANSWER_SUBMITTED 이벤트
 * 6) LEADERBOARD_UPDATED 이벤트
 * 7) GameService.onAnswerCounted → 전원 제출 시 다음 퀴즈로 advance
 *
 * requeue는 false (RabbitConfig). 예외 던지면 DLQ(quiz.answers.dlq)로 이동.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerProcessor {

    private static final int FALLBACK_TIME_LIMIT = 30;

    private final AnswerRepository answerRepository;
    private final QuizRepository quizRepository;
    private final QuizRoomRepository quizRoomRepository;
    private final LeaderboardService leaderboardService;
    private final GameService gameService;
    private final GameStateStore gameStateStore;
    private final RoomEventPublisher roomEventPublisher;

    @Transactional
    @RabbitListener(queues = RabbitConfig.QUEUE_ANSWERS)
    public void onMessage(AnswerQueueMessage msg) {
        log.debug("processor recv roomId={} userId={} quizId={} publisherId={}",
                msg.roomId(), msg.userId(), msg.quizId(), msg.publisherId());

        // 1) 중복 체크 (이미 처리된 답변)
        if (answerRepository.findByParticipantIdAndQuizId(msg.participantId(), msg.quizId()).isPresent()) {
            log.warn("duplicate answer ignored participantId={} quizId={}",
                    msg.participantId(), msg.quizId());
            return;
        }

        // 2) Quiz 로드 (없으면 DLQ)
        Quiz quiz = quizRepository.findById(msg.quizId())
                .orElseThrow(() -> new QuizNotFoundException(msg.quizId()));

        boolean correct = quiz.isCorrect(msg.answer());

        int timeLimit = quiz.getTimeLimit();
        if (timeLimit <= 0) {
            timeLimit = quizRoomRepository.findById(msg.roomId())
                    .map(QuizRoom::getDefaultTimeLimit)
                    .orElse(FALLBACK_TIME_LIMIT);
        }

        int score = ScoreCalculator.calculate(correct, msg.responseTimeMs(), timeLimit);

        // 3) 저장 (unique 제약 충돌 시 ack drop)
        try {
            Answer saved = answerRepository.save(Answer.builder()
                    .participantId(msg.participantId())
                    .quizId(msg.quizId())
                    .answer(msg.answer())
                    .isCorrect(correct)
                    .responseTimeMs(msg.responseTimeMs())
                    .score(score)
                    .build());
            log.info("answer saved id={} participantId={} quizId={} correct={} score={} responseTimeMs={}",
                    saved.getId(), msg.participantId(), msg.quizId(), correct, score, msg.responseTimeMs());
        } catch (DataIntegrityViolationException dup) {
            log.warn("answer unique violation (concurrent duplicate) participantId={} quizId={}: {}",
                    msg.participantId(), msg.quizId(), dup.getMessage());
            return;
        }

        // 4) 정답 → 리더보드 증가
        if (correct && score > 0) {
            leaderboardService.incrementScore(msg.roomId(), msg.userId(), score);
        }

        // 5) answered 카운트 증가 → 집계 이벤트 (정답 여부 비공개)
        long answered = gameStateStore.incrementAnswerCount(msg.roomId(), msg.quizId());

        roomEventPublisher.publishAfterCommit(msg.roomId(), RoomEventType.ANSWER_SUBMITTED, Map.of(
                "userId", msg.userId(),
                "quizId", msg.quizId(),
                "answered", answered
        ));

        // 6) 리더보드 스냅샷 브로드캐스트
        List<LeaderboardEntry> top = leaderboardService.top(msg.roomId(), 10);
        roomEventPublisher.publishAfterCommit(msg.roomId(), RoomEventType.LEADERBOARD_UPDATED, Map.of(
                "leaderboard", top
        ));

        // 7) 전원 제출 판단 → advance
        gameService.onAnswerCounted(msg.roomId(), msg.quizId());
    }
}

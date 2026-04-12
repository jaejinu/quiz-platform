package com.quiz.infra.websocket.controller;

import com.quiz.common.exception.ErrorResponse;
import com.quiz.common.exception.QuizException;
import com.quiz.common.exception.UnauthorizedException;
import com.quiz.domain.answer.AnswerService;
import com.quiz.domain.game.GameService;
import com.quiz.domain.participant.ParticipantService;
import com.quiz.infra.websocket.AuthPrincipal;
import com.quiz.infra.websocket.dto.AnswerSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameStompController {

    private final ParticipantService participantService;
    private final GameService gameService;
    private final AnswerService answerService;

    @MessageMapping("/room/{roomId}/join")
    public void join(@DestinationVariable Long roomId,
                     SimpMessageHeaderAccessor headerAccessor,
                     Principal principal) {
        AuthPrincipal auth = requireAuth(principal);
        String sessionId = headerAccessor.getSessionId();
        log.debug("join roomId={} userId={} sessionId={}", roomId, auth.userId(), sessionId);
        participantService.join(roomId, auth, sessionId);
    }

    @MessageMapping("/room/{roomId}/start")
    public void start(@DestinationVariable Long roomId, Principal principal) {
        AuthPrincipal auth = requireAuth(principal);
        log.debug("start roomId={} userId={}", roomId, auth.userId());
        gameService.start(roomId, auth.userId());
    }

    @MessageMapping("/room/{roomId}/answer")
    public void answer(@DestinationVariable Long roomId,
                       @Payload AnswerSubmission submission,
                       Principal principal) {
        AuthPrincipal auth = requireAuth(principal);
        log.debug("answer roomId={} userId={} quizId={}", roomId, auth.userId(), submission.quizId());
        answerService.accept(roomId, auth, submission);
    }

    @MessageMapping("/room/{roomId}/leave")
    public void leave(@DestinationVariable Long roomId, Principal principal) {
        AuthPrincipal auth = requireAuth(principal);
        log.debug("leave roomId={} userId={}", roomId, auth.userId());
        participantService.leave(roomId, auth.userId());
    }

    @MessageExceptionHandler(QuizException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleQuiz(QuizException e) {
        log.debug("quiz exception code={} message={}", e.getCode(), e.getMessage());
        return ErrorResponse.of(e.getCode(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleUnknown(Exception e) {
        log.error("unexpected STOMP error: {}", e.getMessage(), e);
        return ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");
    }

    private AuthPrincipal requireAuth(Principal principal) {
        if (!(principal instanceof AuthPrincipal auth)) {
            throw new UnauthorizedException("인증되지 않은 연결입니다.");
        }
        return auth;
    }
}

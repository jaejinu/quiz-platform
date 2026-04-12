package com.quiz.api.room;

import com.quiz.api.dto.CreateQuizRequest;
import com.quiz.api.dto.QuizListResponse;
import com.quiz.api.dto.QuizResponse;
import com.quiz.common.security.CurrentUser;
import com.quiz.domain.quiz.QuizService;
import com.quiz.infra.websocket.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HOST가 방에 퀴즈를 등록/조회하는 관리용 엔드포인트.
 * JWT Principal에서 userId를 추출해 방 소유권을 검증한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/rooms/{roomId}/quizzes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOST')")
public class QuizAdminController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<QuizResponse> add(
        @PathVariable Long roomId,
        @Valid @RequestBody CreateQuizRequest request,
        @CurrentUser AuthPrincipal principal
    ) {
        log.debug("POST /api/rooms/{}/quizzes hostId={}", roomId, principal.userId());
        QuizResponse response = quizService.add(roomId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public QuizListResponse list(
        @PathVariable Long roomId,
        @CurrentUser AuthPrincipal principal
    ) {
        log.debug("GET /api/rooms/{}/quizzes hostId={}", roomId, principal.userId());
        return quizService.listForHost(roomId, principal.userId());
    }
}

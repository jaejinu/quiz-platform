package com.quiz.api.room;

import com.quiz.api.dto.CreateQuizRequest;
import com.quiz.api.dto.QuizListResponse;
import com.quiz.api.dto.QuizResponse;
import com.quiz.domain.quiz.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HOST가 방에 퀴즈를 등록/조회하는 관리용 엔드포인트. Step 4 동안 hostId는 쿼리 파라미터.
 * Step 5 JWT 도입 시 Principal에서 추출로 전환한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/rooms/{roomId}/quizzes")
@RequiredArgsConstructor
public class QuizAdminController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<QuizResponse> add(
        @PathVariable Long roomId,
        @RequestParam Long hostId,
        @Valid @RequestBody CreateQuizRequest request
    ) {
        log.debug("POST /api/rooms/{}/quizzes hostId={}", roomId, hostId);
        QuizResponse response = quizService.add(roomId, hostId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public QuizListResponse list(
        @PathVariable Long roomId,
        @RequestParam Long hostId
    ) {
        log.debug("GET /api/rooms/{}/quizzes hostId={}", roomId, hostId);
        return quizService.listForHost(roomId, hostId);
    }
}

package com.quiz.domain.quiz;

import com.quiz.api.dto.CreateQuizRequest;
import com.quiz.api.dto.QuizListResponse;
import com.quiz.api.dto.QuizResponse;
import com.quiz.common.exception.QuizNotFoundException;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizRepository;
    private final RoomService roomService;

    @Transactional
    public QuizResponse add(Long roomId, Long hostId, CreateQuizRequest req) {
        log.debug("add quiz roomId={} hostId={}", roomId, hostId);
        roomService.validateHost(roomId, hostId);

        QuizRoom room = roomService.getEntityOrThrow(roomId);
        int orderIndex = (int) quizRepository.countByRoomId(roomId);
        int timeLimit = (req.timeLimit() == null || req.timeLimit() <= 0)
            ? room.getDefaultTimeLimit()
            : req.timeLimit();

        Quiz quiz = Quiz.builder()
            .roomId(roomId)
            .question(req.question())
            .type(req.type())
            .options(req.options())
            .correctAnswer(req.correctAnswer())
            .timeLimit(timeLimit)
            .orderIndex(orderIndex)
            .imageUrl(req.imageUrl())
            .build();
        quizRepository.save(quiz);
        log.debug("quiz saved id={} orderIndex={}", quiz.getId(), orderIndex);
        return toResponse(quiz, true);
    }

    public QuizListResponse listForHost(Long roomId, Long hostId) {
        log.debug("listForHost roomId={} hostId={}", roomId, hostId);
        roomService.validateHost(roomId, hostId);
        List<QuizResponse> quizzes = quizRepository.findAllByRoomIdOrderByOrderIndexAsc(roomId).stream()
            .map(q -> toResponse(q, true))
            .toList();
        return new QuizListResponse(quizzes);
    }

    public QuizResponse getForPlayer(Long quizId) {
        log.debug("getForPlayer quizId={}", quizId);
        return toResponse(getEntityOrThrow(quizId), false);
    }

    public Quiz getEntityOrThrow(Long id) {
        return quizRepository.findById(id)
            .orElseThrow(() -> new QuizNotFoundException(id));
    }

    private QuizResponse toResponse(Quiz quiz, boolean includeAnswer) {
        return new QuizResponse(
            quiz.getId(),
            quiz.getOrderIndex(),
            quiz.getQuestion(),
            quiz.getType().name(),
            quiz.getOptions(),
            quiz.getTimeLimit(),
            includeAnswer ? quiz.getCorrectAnswer() : null,
            quiz.getImageUrl()
        );
    }
}

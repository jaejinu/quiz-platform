package com.quiz.domain.result;

import com.quiz.common.exception.InvalidGameStateException;
import com.quiz.common.exception.RoomNotFoundException;
import com.quiz.domain.answer.AnswerRepository;
import com.quiz.domain.leaderboard.LeaderboardEntry;
import com.quiz.domain.leaderboard.LeaderboardService;
import com.quiz.domain.participant.ParticipantRepository;
import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.domain.room.RoomStatus;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameResultService {

    private static final int LEADERBOARD_LIMIT = 1000;
    private static final int WORST_LIMIT = 3;
    private static final String UNKNOWN_HOST = "unknown-host";

    private final QuizRoomRepository quizRoomRepository;
    private final QuizRepository quizRepository;
    private final AnswerRepository answerRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    public GameResult build(Long roomId) {
        log.debug("build game result roomId={}", roomId);
        QuizRoom room = quizRoomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.FINISHED) {
            throw new InvalidGameStateException("NOT_FINISHED");
        }

        String hostNickname = userRepository.findById(room.getHostId())
            .map(User::getNickname)
            .orElse(UNKNOWN_HOST);

        int totalParticipants = participantRepository.findAllByRoomId(roomId).size();

        List<Quiz> quizzes = quizRepository.findAllByRoomIdOrderByOrderIndexAsc(roomId);
        List<QuizStats> quizStats = new ArrayList<>(quizzes.size());
        for (Quiz quiz : quizzes) {
            long totalAnswers = answerRepository.countByQuizId(quiz.getId());
            long correctAnswers = answerRepository.countByQuizIdAndIsCorrectTrue(quiz.getId());
            Double avgResp = answerRepository.averageResponseTimeMs(quiz.getId());
            double avgResponseTimeMs = avgResp == null ? 0.0 : avgResp;
            double accuracyRate = totalAnswers == 0 ? 0.0 : (double) correctAnswers / totalAnswers;

            quizStats.add(new QuizStats(
                quiz.getId(),
                quiz.getOrderIndex(),
                quiz.getQuestion(),
                quiz.getCorrectAnswer(),
                quiz.getType().name(),
                totalAnswers,
                correctAnswers,
                accuracyRate,
                avgResponseTimeMs
            ));
        }

        List<LeaderboardEntry> entries = leaderboardService.top(roomId, LEADERBOARD_LIMIT);
        List<LeaderboardSnapshot> leaderboard = new ArrayList<>(entries.size());
        int rank = 1;
        for (LeaderboardEntry e : entries) {
            leaderboard.add(new LeaderboardSnapshot(e.userId(), e.nickname(), e.score(), rank++));
        }

        List<QuizStats> worstThree = quizStats.stream()
            .sorted(Comparator.comparingLong(QuizStats::incorrectCount).reversed())
            .limit(WORST_LIMIT)
            .toList();

        return new GameResult(
            room.getId(),
            room.getCode(),
            room.getTitle(),
            hostNickname,
            room.getStartedAt(),
            room.getFinishedAt(),
            totalParticipants,
            quizzes.size(),
            leaderboard,
            quizStats,
            worstThree
        );
    }
}

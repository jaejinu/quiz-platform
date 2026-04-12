package com.quiz.integration.support;

import com.quiz.domain.quiz.Quiz;
import com.quiz.domain.quiz.QuizRepository;
import com.quiz.domain.quiz.QuizType;
import com.quiz.domain.room.QuizRoom;
import com.quiz.domain.room.QuizRoomRepository;
import com.quiz.domain.room.RoomCodeGenerator;
import com.quiz.domain.user.OAuthProvider;
import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import com.quiz.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 통합 테스트용 엔티티 팩토리. 서비스 레이어를 우회해 Repository에 직접 저장한다.
 * (인증/권한 검증을 건너뛰고 시나리오 setup 속도를 높이기 위함)
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class TestDataFactory {

    private static final List<String> DEFAULT_OPTIONS = List.of("A", "B", "C", "D");

    private final UserRepository userRepository;
    private final QuizRoomRepository quizRoomRepository;
    private final QuizRepository quizRepository;
    private final RoomCodeGenerator roomCodeGenerator;

    // 닉네임/이메일 충돌 방지용 suffix
    private final AtomicLong counter = new AtomicLong();

    public User createUser(String nickname, UserRole role) {
        long n = counter.incrementAndGet();
        String uniqueNickname = nickname + "-" + n;
        User user = User.builder()
                .email(uniqueNickname + "@test.local")
                .nickname(uniqueNickname)
                .passwordHash(null)
                .role(role)
                .oauthProvider(OAuthProvider.LOCAL)
                .oauthId(null)
                .build();
        return userRepository.save(user);
    }

    public User createHost() {
        return createUser("host", UserRole.HOST);
    }

    public User createPlayer(String nickname) {
        return createUser(nickname, UserRole.PLAYER);
    }

    public QuizRoom createRoom(Long hostId, int maxPlayers, int defaultTimeLimit) {
        String code = roomCodeGenerator.generate();
        QuizRoom room = QuizRoom.builder()
                .code(code)
                .hostId(hostId)
                .title("integration-test-room")
                .maxPlayers(maxPlayers)
                .defaultTimeLimit(defaultTimeLimit)
                .build();
        return quizRoomRepository.save(room);
    }

    /**
     * SINGLE 유형 4지선다. correctAnswer은 "A"~"D".
     */
    public Quiz addQuiz(Long roomId, int orderIndex, String question, String correctAnswer, int timeLimit) {
        Quiz quiz = Quiz.builder()
                .roomId(roomId)
                .question(question)
                .type(QuizType.SINGLE)
                .options(DEFAULT_OPTIONS)
                .correctAnswer(correctAnswer)
                .timeLimit(timeLimit)
                .orderIndex(orderIndex)
                .imageUrl(null)
                .build();
        return quizRepository.save(quiz);
    }
}

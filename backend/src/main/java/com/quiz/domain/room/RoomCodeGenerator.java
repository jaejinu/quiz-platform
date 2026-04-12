package com.quiz.domain.room;

import com.quiz.common.exception.RoomCodeGenerationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCodeGenerator {

    // 0, O, 1, I 같은 혼동 문자 제외
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;
    private static final int MAX_RETRIES = 5;

    private final QuizRoomRepository quizRoomRepository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String code = randomCode();
            if (!quizRoomRepository.existsByCode(code)) {
                return code;
            }
            log.warn("Room code collision on attempt {}: {}", attempt, code);
        }
        throw new RoomCodeGenerationFailedException();
    }

    private String randomCode() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}

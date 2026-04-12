package com.quiz.domain.quiz;

import com.quiz.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "quizzes",
    indexes = {
        @Index(name = "idx_quizzes_room_order", columnList = "room_id, order_index")
    }
)
public class Quiz extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 500)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizType type;

    // PostgreSQL JSONB로 저장. SINGLE/MULTIPLE/OX는 선택지 리스트, SHORT는 null 또는 힌트.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    // SINGLE: "A", MULTIPLE: "A,C", OX: "O", SHORT: "정답 문자열"
    @Column(name = "correct_answer", nullable = false, length = 500)
    private String correctAnswer;

    // 초 단위. 0이면 방의 defaultTimeLimit 사용.
    @Column(name = "time_limit", nullable = false)
    private int timeLimit;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder
    private Quiz(Long roomId, String question, QuizType type, List<String> options,
                 String correctAnswer, int timeLimit, int orderIndex, String imageUrl) {
        this.roomId = roomId;
        this.question = question;
        this.type = type;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.timeLimit = timeLimit;
        this.orderIndex = orderIndex;
        this.imageUrl = imageUrl;
    }

    /**
     * 제출된 답변이 정답인지 판정. SHORT 유형의 유사 정답 처리는 상위 서비스에서 확장.
     */
    public boolean isCorrect(String submittedAnswer) {
        if (submittedAnswer == null) {
            return false;
        }
        return switch (type) {
            case SINGLE, OX, SHORT -> correctAnswer.equalsIgnoreCase(submittedAnswer.trim());
            case MULTIPLE -> normalize(correctAnswer).equals(normalize(submittedAnswer));
        };
    }

    private static String normalize(String answer) {
        return java.util.Arrays.stream(answer.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }
}

package com.quiz.domain.room;

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

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "quiz_rooms",
    indexes = {
        @Index(name = "idx_rooms_code", columnList = "code", unique = true),
        @Index(name = "idx_rooms_host", columnList = "host_id"),
        @Index(name = "idx_rooms_status", columnList = "status")
    }
)
public class QuizRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 6자리 입장 코드 (영숫자)
    @Column(nullable = false, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    // 퀴즈당 기본 제한시간(초). Quiz.timeLimit이 없을 때 fallback.
    @Column(name = "default_time_limit", nullable = false)
    private int defaultTimeLimit;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Builder
    private QuizRoom(String code, Long hostId, String title, int maxPlayers, int defaultTimeLimit) {
        this.code = code;
        this.hostId = hostId;
        this.title = title;
        this.maxPlayers = maxPlayers;
        this.defaultTimeLimit = defaultTimeLimit;
        this.status = RoomStatus.WAITING;
    }

    public void start() {
        if (this.status != RoomStatus.WAITING) {
            throw new IllegalStateException("WAITING 상태에서만 시작 가능합니다. 현재: " + this.status);
        }
        this.status = RoomStatus.ACTIVE;
        this.startedAt = Instant.now();
    }

    public void finish() {
        if (this.status != RoomStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태에서만 종료 가능합니다. 현재: " + this.status);
        }
        this.status = RoomStatus.FINISHED;
        this.finishedAt = Instant.now();
    }
}

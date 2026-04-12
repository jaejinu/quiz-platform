package com.quiz.domain.participant;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "participants",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_participants_room_user", columnNames = {"room_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_participants_session", columnList = "session_id")
    }
)
public class Participant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // WebSocket 세션 ID. 재접속 시 갱신.
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Builder
    private Participant(Long roomId, Long userId, String sessionId) {
        this.roomId = roomId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.status = ParticipantStatus.CONNECTED;
        this.joinedAt = Instant.now();
    }

    public void reconnect(String newSessionId) {
        this.sessionId = newSessionId;
        this.status = ParticipantStatus.CONNECTED;
    }

    public void disconnect() {
        this.status = ParticipantStatus.DISCONNECTED;
    }

    public void leave() {
        this.status = ParticipantStatus.LEFT;
        this.sessionId = null;
    }
}

-- V1: 초기 스키마 (users, quiz_rooms, quizzes, participants, answers, refresh_tokens)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    nickname        VARCHAR(50) NOT NULL,
    password_hash   VARCHAR(255),
    role            VARCHAR(20) NOT NULL,
    oauth_provider  VARCHAR(20) NOT NULL,
    oauth_id        VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_oauth ON users (oauth_provider, oauth_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE quiz_rooms (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(6) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    host_id             BIGINT NOT NULL,
    title               VARCHAR(100) NOT NULL,
    max_players         INTEGER NOT NULL,
    default_time_limit  INTEGER NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE,
    finished_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX idx_rooms_code ON quiz_rooms (code);
CREATE INDEX idx_rooms_host ON quiz_rooms (host_id);
CREATE INDEX idx_rooms_status ON quiz_rooms (status);

CREATE TABLE quizzes (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL,
    question        VARCHAR(500) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    options         JSONB,
    correct_answer  VARCHAR(500) NOT NULL,
    time_limit      INTEGER NOT NULL,
    order_index     INTEGER NOT NULL,
    image_url       VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_quizzes_room_order ON quizzes (room_id, order_index);

CREATE TABLE participants (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    session_id  VARCHAR(100),
    status      VARCHAR(20) NOT NULL,
    joined_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_participants_room_user UNIQUE (room_id, user_id)
);
CREATE INDEX idx_participants_session ON participants (session_id);

CREATE TABLE answers (
    id               BIGSERIAL PRIMARY KEY,
    participant_id   BIGINT NOT NULL,
    quiz_id          BIGINT NOT NULL,
    answer           VARCHAR(500) NOT NULL,
    is_correct       BOOLEAN NOT NULL,
    response_time_ms BIGINT NOT NULL,
    score            INTEGER NOT NULL,
    submitted_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_answers_participant_quiz UNIQUE (participant_id, quiz_id)
);
CREATE INDEX idx_answers_quiz ON answers (quiz_id);
CREATE INDEX idx_answers_participant ON answers (participant_id);

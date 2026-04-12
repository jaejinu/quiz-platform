package com.quiz.domain.leaderboard;

/**
 * Redis 키/채널 네이밍 규약. 모든 모듈에서 이 클래스만 사용.
 */
public final class LeaderboardKeys {

    private LeaderboardKeys() {}

    /** Sorted Set — 방별 누적 점수. member=userId, score=누적점수. */
    public static String leaderboard(Long roomId) {
        return "leaderboard:" + roomId;
    }

    /** Pub/Sub 채널 — 방 브로드캐스트 브리지. */
    public static String roomChannel(Long roomId) {
        return "quiz:room:" + roomId;
    }

    /** Pub/Sub 채널 패턴 — 모든 방 구독. */
    public static final String ROOM_CHANNEL_PATTERN = "quiz:room:*";

    /** WebSocket 세션 TTL 키 — 재접속 복구용. */
    public static String sessionKey(String sessionId) {
        return "ws:session:" + sessionId;
    }
}

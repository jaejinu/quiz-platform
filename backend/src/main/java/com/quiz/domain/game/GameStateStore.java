package com.quiz.domain.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class GameStateStore {

    private static final Duration ANSWER_COUNT_TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String publisherId;

    public GameStateStore(StringRedisTemplate stringRedisTemplate,
                          RedisTemplate<String, Object> redisTemplate,
                          @Qualifier("publisherId") String publisherId) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.publisherId = publisherId;
    }

    public void init(Long roomId, Long quizId, int orderIndex, long deadlineMs) {
        long now = System.currentTimeMillis();
        stringRedisTemplate.opsForValue().set(GameStateKeys.currentQuizId(roomId), quizId.toString());
        stringRedisTemplate.opsForValue().set(GameStateKeys.currentQuizIndex(roomId), Integer.toString(orderIndex));
        stringRedisTemplate.opsForValue().set(GameStateKeys.quizStartedAt(roomId), Long.toString(now));
        stringRedisTemplate.opsForValue().set(GameStateKeys.quizDeadline(roomId), Long.toString(deadlineMs));
        stringRedisTemplate.opsForSet().add(GameStateKeys.ACTIVE_ROOMS, roomId.toString());
        log.debug("game state init roomId={} quizId={} orderIndex={} deadlineMs={}",
            roomId, quizId, orderIndex, deadlineMs);
    }

    public void advance(Long roomId, Long nextQuizId, int nextIndex, long newDeadlineMs) {
        long now = System.currentTimeMillis();
        stringRedisTemplate.opsForValue().set(GameStateKeys.currentQuizId(roomId), nextQuizId.toString());
        stringRedisTemplate.opsForValue().set(GameStateKeys.currentQuizIndex(roomId), Integer.toString(nextIndex));
        stringRedisTemplate.opsForValue().set(GameStateKeys.quizStartedAt(roomId), Long.toString(now));
        stringRedisTemplate.opsForValue().set(GameStateKeys.quizDeadline(roomId), Long.toString(newDeadlineMs));
        log.debug("game state advance roomId={} nextQuizId={} nextIndex={} deadlineMs={}",
            roomId, nextQuizId, nextIndex, newDeadlineMs);
    }

    public GameStateSnapshot snapshot(Long roomId) {
        Long quizId = getCurrentQuizId(roomId);
        Integer index = getCurrentQuizIndex(roomId);
        Long startedAt = getQuizStartedAt(roomId);
        Long deadline = getDeadline(roomId);
        boolean active = quizId != null;
        return new GameStateSnapshot(quizId, index, startedAt, deadline, active);
    }

    public Long getCurrentQuizId(Long roomId) {
        String v = stringRedisTemplate.opsForValue().get(GameStateKeys.currentQuizId(roomId));
        return v == null ? null : Long.parseLong(v);
    }

    public Integer getCurrentQuizIndex(Long roomId) {
        String v = stringRedisTemplate.opsForValue().get(GameStateKeys.currentQuizIndex(roomId));
        return v == null ? null : Integer.parseInt(v);
    }

    public Long getDeadline(Long roomId) {
        String v = stringRedisTemplate.opsForValue().get(GameStateKeys.quizDeadline(roomId));
        return v == null ? null : Long.parseLong(v);
    }

    public Long getQuizStartedAt(Long roomId) {
        String v = stringRedisTemplate.opsForValue().get(GameStateKeys.quizStartedAt(roomId));
        return v == null ? null : Long.parseLong(v);
    }

    /**
     * 분산 환경에서 advance 싱글톤 보장용 lock.
     * SET NX PX 5000 — 소유자=publisherId. 5초 TTL로 노드 장애 시 자동 해제.
     */
    public boolean tryAcquireAdvanceLock(Long roomId, Long quizId) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
            GameStateKeys.advanceLock(roomId, quizId),
            publisherId,
            Duration.ofSeconds(5)
        );
        boolean acquired = Boolean.TRUE.equals(ok);
        log.debug("advance lock acquire roomId={} quizId={} publisherId={} acquired={}",
            roomId, quizId, publisherId, acquired);
        return acquired;
    }

    /**
     * 답변 카운터 INCR. TTL 1시간을 매번 갱신해도 무해.
     * D 에이전트 포함 모든 호출자는 이 메서드만 사용 (INCR 집중).
     */
    public long incrementAnswerCount(Long roomId, Long quizId) {
        String key = GameStateKeys.answerCount(roomId, quizId);
        Long v = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, ANSWER_COUNT_TTL);
        return v == null ? 0L : v;
    }

    public long getAnswerCount(Long roomId, Long quizId) {
        String v = stringRedisTemplate.opsForValue().get(GameStateKeys.answerCount(roomId, quizId));
        return v == null ? 0L : Long.parseLong(v);
    }

    public void clear(Long roomId) {
        stringRedisTemplate.delete(GameStateKeys.allGameKeys(roomId));
        stringRedisTemplate.opsForSet().remove(GameStateKeys.ACTIVE_ROOMS, roomId.toString());
        log.debug("game state cleared roomId={}", roomId);
    }

    public Set<Long> getActiveRoomIds() {
        Set<String> members = stringRedisTemplate.opsForSet().members(GameStateKeys.ACTIVE_ROOMS);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>(members.size());
        for (String m : members) {
            try {
                result.add(Long.parseLong(m));
            } catch (NumberFormatException e) {
                log.warn("invalid roomId in active_rooms: {}", m);
            }
        }
        return result;
    }
}

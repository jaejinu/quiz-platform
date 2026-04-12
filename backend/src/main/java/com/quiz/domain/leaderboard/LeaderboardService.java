package com.quiz.domain.leaderboard;

import com.quiz.domain.user.User;
import com.quiz.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    public double incrementScore(Long roomId, Long userId, int delta) {
        Double score = redisTemplate.opsForZSet().incrementScore(
            LeaderboardKeys.leaderboard(roomId),
            userId.toString(),
            (double) delta
        );
        return score == null ? 0.0 : score;
    }

    public List<LeaderboardEntry> top(Long roomId, int n) {
        if (n <= 0) {
            return List.of();
        }
        String key = LeaderboardKeys.leaderboard(roomId);
        Set<TypedTuple<Object>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = new ArrayList<>(tuples.size());
        for (TypedTuple<Object> t : tuples) {
            Object v = t.getValue();
            if (v == null) continue;
            try {
                userIds.add(Long.parseLong(v.toString()));
            } catch (NumberFormatException e) {
                log.warn("invalid member in leaderboard key={} value={}", key, v);
            }
        }

        Map<Long, String> nicknameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                nicknameById.put(u.getId(), u.getNickname());
            }
        }

        List<LeaderboardEntry> out = new ArrayList<>(tuples.size());
        for (TypedTuple<Object> t : tuples) {
            Object v = t.getValue();
            Double score = t.getScore();
            if (v == null || score == null) continue;
            Long userId;
            try {
                userId = Long.parseLong(v.toString());
            } catch (NumberFormatException e) {
                continue;
            }
            String nickname = nicknameById.getOrDefault(userId, "unknown");
            out.add(new LeaderboardEntry(userId, nickname, (int) score.doubleValue()));
        }
        return out;
    }

    public void finalizeAndExpire(Long roomId, Duration ttl) {
        redisTemplate.expire(LeaderboardKeys.leaderboard(roomId), ttl);
        log.debug("leaderboard finalize roomId={} ttl={}", roomId, ttl);
    }

    public void clear(Long roomId) {
        redisTemplate.delete(LeaderboardKeys.leaderboard(roomId));
        log.debug("leaderboard cleared roomId={}", roomId);
    }
}

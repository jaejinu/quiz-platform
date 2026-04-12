package com.quiz.integration.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 각 @Test 메서드 전에 PostgreSQL/Redis/RabbitMQ 상태를 초기화한다.
 * 컨테이너는 재사용(withReuse) 하므로 DB/Redis/Queue 내용은 수동으로 비워야 한다.
 *
 * Rabbit의 큐/exchange는 @Configuration의 Bean이 부팅 시 선언하므로
 * 메시지만 purge하면 충분하다 (토폴로지는 유지).
 */
@Slf4j
@Component
@Profile("test")
@RequiredArgsConstructor
public class ContainerReset {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final AmqpAdmin amqpAdmin;

    public void resetAll() {
        truncateTables();
        flushRedis();
        purgeQueues();
    }

    private void truncateTables() {
        // 역순으로 CASCADE — FK 없는 구조이긴 하지만 일관된 순서 유지.
        try {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE answers, participants, quizzes, quiz_rooms, users RESTART IDENTITY CASCADE"
            );
        } catch (Exception e) {
            // 최초 실행(스키마 생성 직전)에는 테이블이 없을 수 있음 — 무시.
            log.debug("truncate skipped (tables not yet created): {}", e.getMessage());
        }
    }

    private void flushRedis() {
        RedisConnectionFactory factory = stringRedisTemplate.getConnectionFactory();
        if (factory == null) {
            return;
        }
        try (RedisConnection conn = factory.getConnection()) {
            conn.serverCommands().flushAll();
        } catch (Exception e) {
            log.warn("redis flushAll failed: {}", e.getMessage());
        }
    }

    private void purgeQueues() {
        purgeQuiet("quiz.answers");
        purgeQuiet("quiz.answers.dlq");
    }

    private void purgeQuiet(String queue) {
        try {
            if (amqpAdmin instanceof RabbitAdmin rabbitAdmin) {
                rabbitAdmin.purgeQueue(queue, false);
            } else {
                amqpAdmin.purgeQueue(queue, false);
            }
        } catch (Exception e) {
            // 큐가 아직 선언되지 않은 첫 실행 때 흔함.
            log.debug("purge queue {} skipped: {}", queue, e.getMessage());
        }
    }
}

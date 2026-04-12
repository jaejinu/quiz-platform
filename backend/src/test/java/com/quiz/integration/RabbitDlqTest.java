package com.quiz.integration;

import com.quiz.infra.rabbitmq.AnswerQueueMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitDlqTest extends AbstractIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    @Qualifier("publisherId")
    String publisherId;

    @Test
    void nonExistentQuizId_isRoutedToDlq() throws InterruptedException {
        AnswerQueueMessage msg = new AnswerQueueMessage(
                1L,
                1L,
                42L,
                "test",
                99999L,
                "A",
                100L,
                Instant.now(),
                publisherId
        );

        rabbitTemplate.convertAndSend("quiz.exchange", "answer.submitted", msg);

        QueueInformation info = pollForDlqMessage("quiz.answers.dlq", 10_000, 500);

        assertThat(info).as("DLQ queue info").isNotNull();
        assertThat(info.getMessageCount())
                .as("message count in quiz.answers.dlq")
                .isGreaterThanOrEqualTo(1);
    }

    private QueueInformation pollForDlqMessage(String queueName, long timeoutMs, long intervalMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        QueueInformation info = null;
        while (System.currentTimeMillis() < deadline) {
            info = rabbitAdmin.getQueueInfo(queueName);
            if (info != null && info.getMessageCount() >= 1) {
                return info;
            }
            Thread.sleep(intervalMs);
        }
        return info;
    }
}

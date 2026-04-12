package com.quiz.infra.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 토폴로지 및 리스너 설정.
 *
 * quiz.exchange (direct) --[answer.submitted]--> quiz.answers
 *                                                  │ (dead letter)
 *                                                  ▼
 *                                       quiz.dlx (direct) --[answer.failed]--> quiz.answers.dlq
 */
@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    public static final String EXCHANGE = "quiz.exchange";
    public static final String QUEUE_ANSWERS = "quiz.answers";
    public static final String ROUTING_KEY = "answer.submitted";

    public static final String DLX = "quiz.dlx";
    public static final String DLQ = "quiz.answers.dlq";
    public static final String DLQ_ROUTING = "answer.failed";

    private final ObjectMapper objectMapper;

    @Bean
    public DirectExchange quizExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange quizDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue answersQueue() {
        return QueueBuilder.durable(QUEUE_ANSWERS)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING)
                .build();
    }

    @Bean
    public Queue answersDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding answersBinding(Queue answersQueue, DirectExchange quizExchange) {
        return BindingBuilder.bind(answersQueue).to(quizExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue answersDlq, DirectExchange quizDlx) {
        return BindingBuilder.bind(answersDlq).to(quizDlx).with(DLQ_ROUTING);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(16);
        factory.setPrefetchCount(20);
        // 실패 시 재큐 금지 → DLX로 라우팅
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        return factory;
    }
}

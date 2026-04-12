package com.quiz.integration;

import com.quiz.integration.support.ContainerReset;
import com.quiz.integration.support.StompTestClient;
import com.quiz.integration.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 공통 base. Testcontainers로 PostgreSQL/Redis/RabbitMQ를 띄우고
 * 각 @Test 시작 전 {@link ContainerReset#resetAll()}로 상태를 초기화한다.
 *
 * 컨테이너는 {@code withReuse(true)}로 설정되어 로컬 개발 시
 * {@code ~/.testcontainers.properties} 에 {@code testcontainers.reuse.enable=true} 를
 * 설정하면 JVM 간 재사용된다. (CI에서는 무시되고 매번 새로 뜸.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quizdb")
            .withUsername("quiz")
            .withPassword("quiz")
            .withReuse(true);

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @Container
    static RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withReuse(true);

    @DynamicPropertySource
    static void bind(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ContainerReset reset;

    @Autowired
    protected TestDataFactory factory;

    @Autowired
    protected StompTestClient stompClient;

    @BeforeEach
    void resetState() {
        reset.resetAll();
    }
}

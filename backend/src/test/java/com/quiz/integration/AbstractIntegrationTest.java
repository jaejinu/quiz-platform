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

/**
 * 통합 테스트 공통 base. Testcontainers로 PostgreSQL/Redis/RabbitMQ를 띄우고
 * 각 @Test 시작 전 {@link ContainerReset#resetAll()}로 상태를 초기화한다.
 *
 * <h3>Singleton Container pattern</h3>
 * {@code @Container} + {@code @Testcontainers} 조합은 테스트 클래스 단위 lifecycle이라
 * 클래스 경계에서 컨테이너 start/stop이 반복되며 포트 매핑이 바뀌어 Spring
 * TestContext 캐시와 불일치한다 (Connection refused).
 *
 * <p>대신 static initializer에서 한 번만 {@code start()}를 호출하고 JVM 종료까지
 * 재사용한다. Spring이 {@code @DynamicPropertySource}로 포트를 바인딩하면
 * 모든 후속 테스트가 같은 포트를 쓴다.
 *
 * <p>로컬 개발 시 {@code ~/.testcontainers.properties} 에
 * {@code testcontainers.reuse.enable=true} 를 설정하면 {@code withReuse}가 활성화되어
 * JVM 간 재사용 (반복 실행 시 빠름). CI에서는 reuse 미설정으로 매 run에 새로 뜸.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quizdb")
            .withUsername("quiz")
            .withPassword("quiz")
            .withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
        RABBIT.start();
    }

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

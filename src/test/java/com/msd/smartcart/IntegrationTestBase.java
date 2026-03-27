package com.msd.smartcart;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para todos los tests de integración.
 *
 * Los tres contenedores se levantan una sola vez para toda la suite (@Container
 * estático con @Testcontainers) y se reutilizan entre tests para minimizar el
 * tiempo de arranque. Cada test es responsable de limpiar su propio estado
 * en @BeforeEach / @AfterEach para garantizar aislamiento.
 *
 * Uso: extender esta clase en cada test de integración.
 */
@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class IntegrationTestBase {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0"));

    @Container
    static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("security.jwt.secret",
                () -> "integration-test-secret-key-minimum-256-bits-long!!");
        registry.add("security.jwt.expiration-ms", () -> "86400000");
    }
}
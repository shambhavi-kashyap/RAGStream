package com.rag.ingestion;

import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    private static final boolean DOCKER_AVAILABLE = isDockerAvailable();

    static {
        System.setProperty("testcontainers.docker.client.strategy",
                "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy");
        System.setProperty("DOCKER_HOST", "tcp://localhost:2375");
    }

    @Container
    protected static final PostgreSQLContainer<?> postgres = createPostgres();

    @Container
    protected static final KafkaContainer kafka = createKafka();

    static {
        if (DOCKER_AVAILABLE) {
            postgres.start();
            kafka.start();
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @AfterAll
    static void stopContainers() {
        if (kafka != null && kafka.isRunning()) {
            kafka.stop();
        }
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    private static PostgreSQLContainer<?> createPostgres() {
        if (!DOCKER_AVAILABLE) {
            return null;
        }

        DockerImageName postgresImage = DockerImageName.parse("postgres:16-alpine");
        PostgreSQLContainer<?> container = null;
        try {
            container = new PostgreSQLContainer<>(postgresImage);
            container.withDatabaseName("ragstream_metadata")
                    .withUsername("ragadmin")
                    .withPassword("StrongEnterprisePassword2026!");
            return container;
        } catch (Exception ex) {
            if (container != null) {
                container.close();
            }
            throw ex;
        }
    }

    private static KafkaContainer createKafka() {
        if (!DOCKER_AVAILABLE) {
            return null;
        }

        DockerImageName kafkaImage = DockerImageName.parse("confluentinc/cp-kafka:7.4.0");
        KafkaContainer container = null;
        try {
            container = new KafkaContainer(kafkaImage);
            return container;
        } catch (Exception ex) {
            if (container != null) {
                container.close();
            }
            throw ex;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
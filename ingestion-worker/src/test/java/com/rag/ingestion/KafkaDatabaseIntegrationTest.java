package com.rag.ingestion;

import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;

@EnabledIf("isDockerAvailable")
class KafkaDatabaseIntegrationTest extends AbstractIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
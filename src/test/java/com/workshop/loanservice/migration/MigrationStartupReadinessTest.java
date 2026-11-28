package com.workshop.loanservice.migration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MigrationStartupReadinessTest {

    private static final AtomicReference<MigrationStatus.State> STATE_WHEN_SERVER_STARTED = new AtomicReference<>();

    @TestConfiguration
    static class ServerStartProbe {
        @Bean
        ApplicationListener<ServletWebServerInitializedEvent> recordMigrationState(MigrationStatus status) {
            return event -> STATE_WHEN_SERVER_STARTED.set(status.getState());
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    void migrationCompletesBeforeTheServerAcceptsRequests() {
        assertThat(STATE_WHEN_SERVER_STARTED.get()).isEqualTo(MigrationStatus.State.COMPLETED);
    }

    @Test
    void readinessIncludesMigrationHealth() {
        JsonNode readiness = http.getForObject("/actuator/health/readiness", JsonNode.class);
        assertThat(readiness.path("status").asText()).isEqualTo("UP");
        assertThat(readiness.path("components").path("migration").path("status").asText()).isEqualTo("UP");

        JsonNode migration = http.getForObject("/actuator/health/migration", JsonNode.class);
        assertThat(migration.path("details").path("tables").path("payments").path("inserted").asInt()).isEqualTo(10);
        assertThat(migration.path("details").path("mismatches").size()).isZero();
    }

    @Test
    void migrationMetricsAndInfoAreExposed() {
        JsonNode inserted = http.getForObject(
                "/actuator/metrics/loan.migration.rows?tag=table:payments&tag=outcome:inserted", JsonNode.class);
        assertThat(inserted.path("measurements").get(0).path("value").asDouble()).isEqualTo(10.0);

        JsonNode mismatches = http.getForObject("/actuator/metrics/loan.migration.reconciliation.mismatches", JsonNode.class);
        assertThat(mismatches.path("measurements").get(0).path("value").asDouble()).isZero();

        JsonNode duration = http.getForObject("/actuator/metrics/loan.migration.duration?tag=outcome:success", JsonNode.class);
        assertThat(duration.path("measurements").get(0).path("value").asDouble()).isEqualTo(1.0);

        JsonNode info = http.getForObject("/actuator/info", JsonNode.class);
        assertThat(info.path("app").path("datasource-mode").asText()).isEqualTo("modern");
    }
}

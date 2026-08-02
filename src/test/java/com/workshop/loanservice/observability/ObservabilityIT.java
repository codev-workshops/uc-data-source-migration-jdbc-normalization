package com.workshop.loanservice.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability, asserted against a real server rather than MockMvc: the Prometheus endpoint is
 * served by the actuator's own handler mapping, so a mocked dispatcher would prove nothing about
 * what a scrape actually returns.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "loanservice.read-source=dual_read",
    "loanservice.v1.large-response-warn-threshold=2",
    "management.prometheus.metrics.export.enabled=true"
})
// Boot disables metrics export in tests by default; this test exists precisely to assert the export.
@AutoConfigureObservability
class ObservabilityIT {

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void generateTraffic() {
        assertThat(rest.getForEntity("/api/loans", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheusExposesApiLatencyPoolAndJvmMetrics() {
        String scrape = scrape();

        assertThat(scrape)
            .contains("http_server_requests_seconds")
            .contains("hikaricp_connections")
            .contains("jvm_memory_used_bytes")
            .contains("application=\"loan-service\"");
    }

    /** P50/P90/P95/P99 need server-side buckets, not an average of per-instance averages. */
    @Test
    void httpLatencyIsExportedAsAHistogram() {
        assertThat(scrape()).contains("http_server_requests_seconds_bucket");
    }

    @Test
    void bothConnectionPoolsAreInstrumentedSeparately() {
        String scrape = scrape();

        assertThat(scrape).contains("modern-pool").contains("legacy-pool");
    }

    @Test
    void shadowReadComparisonIsCounted() {
        assertThat(scrape()).contains("loanservice_shadow_read_total");
    }

    /** v1 stays unbounded; the only reaction to an oversized response is a metric and a warning. */
    @Test
    void oversizedV1ResponseIsCountedButStillServedInFull() {
        ResponseEntity<String> response = rest.getForEntity("/api/loans", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("LN-2019-00142", "LN-2021-00567");
        assertThat(scrape()).contains("loanservice_v1_large_response_total");
    }

    @Test
    void migrationAndReconciliationOutcomesAreVisibleAsMetrics() {
        String scrape = scrape();

        assertThat(scrape).contains("loanservice_migration_rows");
        assertThat(scrape).contains("loanservice_reconciliation_drift");
    }

    /** Actuator must not hand out configuration, beans or heap dumps. */
    @Test
    void sensitiveActuatorEndpointsAreNotExposed() {
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/heapdump", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/beans", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthDoesNotLeakComponentDetails() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).doesNotContain("jdbc:h2", "components");
    }

    @Test
    void h2ConsoleIsNotReachable() {
        assertThat(rest.getForEntity("/h2-console", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String scrape() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}

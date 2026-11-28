package com.workshop.loanservice.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationHealthIndicatorTest {

    private final MigrationStatus status = new MigrationStatus();
    private final MigrationHealthIndicator indicator = new MigrationHealthIndicator(status);

    @Test
    void reportsEachState() {
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);

        status.started();
        assertThat(indicator.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        status.failed(new IllegalStateException("legacy database unavailable"));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails().get("error"))
                .isEqualTo("IllegalStateException: legacy database unavailable");
    }

    @Test
    void rejectionsAloneAreHealthyButMismatchesAreNot() {
        MigrationReport report = new MigrationReport();
        report.table("payments").recordRejected("PMT-1", "bad date");

        status.completed(report, List.of(new ReconciliationCheck("payments row count", "9", "9", true)));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

        status.completed(report, List.of(new ReconciliationCheck("payments row count", "9", "8", false)));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails().get("mismatches"))
                .isEqualTo(List.of("[MISMATCH] payments row count legacy=9 modern=8"));
    }
}

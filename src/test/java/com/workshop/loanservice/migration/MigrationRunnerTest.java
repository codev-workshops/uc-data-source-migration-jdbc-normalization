package com.workshop.loanservice.migration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationRunnerTest {

    private final LegacyToModernMigrator migrator = mock(LegacyToModernMigrator.class);
    private final MigrationStatus status = new MigrationStatus();
    private final MigrationReport report = new MigrationReport();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void failureIsRecordedAndOnlyAbortsStartupWhenFailOnErrorIsSet() {
        when(migrator.migrate()).thenThrow(new IllegalStateException("legacy database unavailable"));

        assertThatCode(() -> new MigrationRunner(migrator, status, registry, false).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
        assertThat(status.getState()).isEqualTo(MigrationStatus.State.FAILED);
        assertThat(registry.get("loan.migration.duration").tag("outcome", "failure").timer().count()).isEqualTo(1);

        assertThatThrownBy(() -> new MigrationRunner(migrator, status, registry, true).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Legacy-to-modern migration failed");
    }

    @Test
    void mismatchIsRecordedAndOnlyAbortsStartupWhenFailOnErrorIsSet() {
        ReconciliationCheck mismatch = new ReconciliationCheck("payments row count", "10", "9", false);
        when(migrator.migrate()).thenReturn(report);
        when(migrator.reconcile(any())).thenReturn(List.of(mismatch));

        new MigrationRunner(migrator, status, registry, false).afterSingletonsInstantiated();
        assertThat(status.getState()).isEqualTo(MigrationStatus.State.COMPLETED);
        assertThat(status.getMismatches()).containsExactly(mismatch);
        assertThat(registry.get("loan.migration.reconciliation.mismatches").gauge().value()).isEqualTo(1);

        assertThatThrownBy(() -> new MigrationRunner(migrator, status, registry, true).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 MISMATCH(es)");
    }

    @Test
    void cleanRunCompletesAndRecordsMetrics() {
        MigrationReport.TableReport payments = report.table("payments");
        payments.recordRead();
        payments.recordInserted();
        payments.recordRead();
        payments.recordRejected("PMT-1", "bad date");
        when(migrator.migrate()).thenReturn(report);
        when(migrator.reconcile(report)).thenReturn(List.of(new ReconciliationCheck("payments row count", "10", "10", true)));

        new MigrationRunner(migrator, status, registry, true).afterSingletonsInstantiated();

        assertThat(status.getState()).isEqualTo(MigrationStatus.State.COMPLETED);
        assertThat(status.getMismatches()).isEmpty();
        assertThat(registry.get("loan.migration.rows").tags("table", "payments", "outcome", "read").counter().count()).isEqualTo(2);
        assertThat(registry.get("loan.migration.rows").tags("table", "payments", "outcome", "inserted").counter().count()).isEqualTo(1);
        assertThat(registry.get("loan.migration.rows").tags("table", "payments", "outcome", "rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("loan.migration.duration").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("loan.migration.reconciliation.checks").gauge().value()).isEqualTo(1);
        assertThat(registry.get("loan.migration.reconciliation.mismatches").gauge().value()).isZero();
    }
}

package com.workshop.loanservice.migration;

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

    @Test
    void failureIsRecordedAndOnlyAbortsStartupWhenFailOnErrorIsSet() {
        when(migrator.migrate()).thenThrow(new IllegalStateException("legacy database unavailable"));

        assertThatCode(() -> new MigrationRunner(migrator, status, false).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
        assertThat(status.getState()).isEqualTo(MigrationStatus.State.FAILED);

        assertThatThrownBy(() -> new MigrationRunner(migrator, status, true).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Legacy-to-modern migration failed");
    }

    @Test
    void mismatchIsRecordedAndOnlyAbortsStartupWhenFailOnErrorIsSet() {
        ReconciliationCheck mismatch = new ReconciliationCheck("payments row count", "10", "9", false);
        when(migrator.migrate()).thenReturn(report);
        when(migrator.reconcile(any())).thenReturn(List.of(mismatch));

        new MigrationRunner(migrator, status, false).afterSingletonsInstantiated();
        assertThat(status.getState()).isEqualTo(MigrationStatus.State.COMPLETED);
        assertThat(status.getMismatches()).containsExactly(mismatch);

        assertThatThrownBy(() -> new MigrationRunner(migrator, status, true).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 MISMATCH(es)");
    }

    @Test
    void cleanRunCompletes() {
        when(migrator.migrate()).thenReturn(report);
        when(migrator.reconcile(report)).thenReturn(List.of(new ReconciliationCheck("payments row count", "10", "10", true)));

        new MigrationRunner(migrator, status, true).afterSingletonsInstantiated();

        assertThat(status.getState()).isEqualTo(MigrationStatus.State.COMPLETED);
        assertThat(status.getMismatches()).isEmpty();
    }
}

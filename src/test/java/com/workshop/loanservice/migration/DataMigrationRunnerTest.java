package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataMigrationRunnerTest {

    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);

    private final DataMigrationService migrationService = mock(DataMigrationService.class);
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final SimpleLock lock = mock(SimpleLock.class);

    private DataMigrationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DataMigrationRunner(migrationService, lockProvider, true, LOCK_AT_MOST_FOR);
    }

    @Test
    void skipsMigrationWhenAnotherInstanceHoldsTheLock() {
        when(lockProvider.lock(any())).thenReturn(Optional.empty());

        runner.run();

        verifyNoInteractions(migrationService);
    }

    @Test
    void migratesUnderTheNamedLockAndReleasesIt() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
        when(migrationService.migrate(true)).thenReturn(new MigrationReport());

        runner.run();

        ArgumentCaptor<LockConfiguration> config = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(lockProvider).lock(config.capture());
        assertThat(config.getValue().getName()).isEqualTo(DataMigrationRunner.LOCK_NAME);
        assertThat(config.getValue().getLockAtMostFor()).isEqualTo(LOCK_AT_MOST_FOR);
        verify(migrationService).migrate(true);
        verify(lock).unlock();
    }

    @Test
    void releasesTheLockWhenMigrationFails() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
        when(migrationService.migrate(true)).thenThrow(new MigrationFailedException(new MigrationReport()));

        assertThatThrownBy(() -> runner.run()).isInstanceOf(MigrationFailedException.class);

        verify(lock).unlock();
    }
}

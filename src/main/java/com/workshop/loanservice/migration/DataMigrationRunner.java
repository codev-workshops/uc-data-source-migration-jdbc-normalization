package com.workshop.loanservice.migration;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates the modern schema from the legacy CDW tables at startup (toggle with {@code migration.run-on-startup}).
 *
 * <p>The run holds a ShedLock lease ({@value #LOCK_NAME}) until the migration transaction has committed or
 * rolled back. An instance that finds the lease taken skips the migration instead of racing the holder on
 * unique keys; the next start re-runs it idempotently.
 */
@Component
@ConditionalOnProperty(name = "migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class DataMigrationRunner implements CommandLineRunner {

    static final String LOCK_NAME = "legacy-to-modern-migration";

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final DataMigrationService migrationService;
    private final LockProvider lockProvider;
    private final boolean failOnError;
    private final Duration lockAtMostFor;

    public DataMigrationRunner(DataMigrationService migrationService,
                               LockProvider lockProvider,
                               @Value("${migration.fail-on-error:true}") boolean failOnError,
                               @Value("${migration.lock-at-most-for:PT30M}") Duration lockAtMostFor) {
        this.migrationService = migrationService;
        this.lockProvider = lockProvider;
        this.failOnError = failOnError;
        this.lockAtMostFor = lockAtMostFor;
    }

    @Override
    public void run(String... args) {
        Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(Instant.now(), LOCK_NAME, lockAtMostFor, Duration.ZERO));
        if (lock.isEmpty()) {
            log.info("Legacy -> modern migration SKIPPED: another instance holds the '{}' lock", LOCK_NAME);
            return;
        }
        try {
            migrate();
        } finally {
            lock.get().unlock();
        }
    }

    private void migrate() {
        MigrationReport report;
        try {
            report = migrationService.migrate(failOnError);
        } catch (MigrationFailedException e) {
            log.error(e.getReport().summary());
            throw e;
        }
        if (report.isSuccessful()) {
            log.info(report.summary());
        } else {
            log.error(report.summary());
        }
    }
}

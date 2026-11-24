package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the legacy-to-modern migration and reconciliation during startup.
 *
 * <p>It runs once all singletons exist and before the embedded web server starts
 * accepting connections, so no request is served from a partially migrated schema.
 * A failure or reconciliation mismatch is recorded in {@link MigrationStatus} and reported as DOWN
 * by the {@code migration} health indicator (part of the readiness group). With
 * {@code loan.migration.fail-on-error=true} it also aborts startup.
 */
@Component
@ConditionalOnProperty(name = "loan.migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationRunner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyToModernMigrator migrator;
    private final MigrationStatus status;
    private final boolean failOnError;

    public MigrationRunner(LegacyToModernMigrator migrator, MigrationStatus status,
                           @Value("${loan.migration.fail-on-error:false}") boolean failOnError) {
        this.migrator = migrator;
        this.status = status;
        this.failOnError = failOnError;
    }

    @Override
    public void afterSingletonsInstantiated() {
        status.started();
        try {
            MigrationReport report = migrator.migrate();
            log.info("Legacy-to-modern migration finished:{}", report);

            List<ReconciliationCheck> checks = migrator.reconcile(report);
            checks.forEach(check -> {
                if (check.passed()) {
                    log.info("Reconciliation {}", check);
                } else {
                    log.warn("Reconciliation {}", check);
                }
            });
            status.completed(report, checks);
        } catch (RuntimeException e) {
            log.error("Legacy-to-modern migration failed", e);
            status.failed(e);
            if (failOnError) {
                throw new IllegalStateException("Legacy-to-modern migration failed", e);
            }
            return;
        }

        List<ReconciliationCheck> mismatches = status.getMismatches();
        if (mismatches.isEmpty()) {
            log.info("Reconciliation passed: {} checks", status.getChecks().size());
            return;
        }
        log.error("Reconciliation found {} MISMATCH(es) out of {} checks: {}",
                mismatches.size(), status.getChecks().size(), mismatches);
        if (failOnError) {
            throw new IllegalStateException("Reconciliation found " + mismatches.size() + " MISMATCH(es): " + mismatches);
        }
    }
}

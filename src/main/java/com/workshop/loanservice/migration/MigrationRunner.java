package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the legacy-to-modern migration and reconciliation during startup.
 *
 * <p>It runs once all singletons exist and before the embedded web server starts
 * accepting connections, so no request is served from a partially migrated schema.
 * A failure doesn't stop startup; it is recorded in {@link MigrationStatus} and
 * reported as DOWN by the {@code migration} health indicator (part of the readiness group).
 */
@Component
@ConditionalOnProperty(name = "loan.migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationRunner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyToModernMigrator migrator;
    private final MigrationStatus status;

    public MigrationRunner(LegacyToModernMigrator migrator, MigrationStatus status) {
        this.migrator = migrator;
        this.status = status;
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
        }
    }
}

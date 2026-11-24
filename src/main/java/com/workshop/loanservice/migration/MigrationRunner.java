package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the legacy-to-modern migration and reconciliation on startup.
 */
@Component
@ConditionalOnProperty(name = "loan.migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyToModernMigrator migrator;

    public MigrationRunner(LegacyToModernMigrator migrator) {
        this.migrator = migrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationReport report = migrator.migrate();
        log.info("Legacy-to-modern migration finished:{}", report);

        List<ReconciliationCheck> checks = migrator.reconcile();
        checks.forEach(check -> {
            if (check.passed()) {
                log.info("Reconciliation {}", check);
            } else {
                log.warn("Reconciliation {}", check);
            }
        });
    }
}

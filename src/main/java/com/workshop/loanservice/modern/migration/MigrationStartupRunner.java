package com.workshop.loanservice.modern.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates the modern schema from the legacy CDW tables at application
 * startup so the app serves migrated data from its first request. The
 * migration is idempotent, including when a prior run was only partially
 * completed.
 *
 * <p>Disable with {@code migration.run-on-startup=false} (used by tests that
 * exercise the migration service against empty modern tables).
 */
@Component
@ConditionalOnProperty(name = "migration.run-on-startup", havingValue = "true")
public class MigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationStartupRunner.class);

    private final LegacyToModernMigrationService migrationService;

    public MigrationStartupRunner(LegacyToModernMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running idempotent legacy-to-modern migration");
        migrationService.migrateAll();
    }
}

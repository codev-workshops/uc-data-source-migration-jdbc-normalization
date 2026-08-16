package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the migration at startup so the modern data source is populated before the
 * first request. Disable with {@code loanservice.migration.run-on-startup=false}
 * when the modern data source is already loaded by another process.
 */
@Component
@ConditionalOnProperty(name = "loanservice.migration.run-on-startup", havingValue = "true",
        matchIfMissing = true)
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationService migrationService;

    public MigrationRunner(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationReport report = migrationService.migrate();
        if (!report.isClean()) {
            log.warn("Migration completed with rejected records: {}", report);
        }
    }
}

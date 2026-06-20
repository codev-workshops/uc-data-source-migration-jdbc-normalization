package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy -> modern data migration once on startup so the modern schema
 * is populated before the application serves requests from it. Because the
 * migration is idempotent, subsequent restarts re-run it harmlessly (all records
 * are skipped).
 */
@Component
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final DataMigrationService migrationService;

    public MigrationRunner(DataMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting legacy -> modern data migration");
        MigrationReport report = migrationService.migrate();
        if (report.hasFailures()) {
            log.error("Data migration completed WITH FAILURES: {}", report);
        } else {
            log.info("Data migration completed successfully: {}", report);
        }
    }
}

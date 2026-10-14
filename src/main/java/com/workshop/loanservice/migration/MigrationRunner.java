package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs the legacy-to-modern migration at startup when {@code app.migration.run-on-startup}. */
@Component
@ConditionalOnProperty(name = "app.migration.run-on-startup", havingValue = "true")
class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyDataMigrator migrator;

    MigrationRunner(LegacyDataMigrator migrator) {
        this.migrator = migrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationReport report = migrator.migrate();
        if (report.isClean()) {
            log.info("Legacy data migration finished: {}", report);
        } else {
            log.warn("Legacy data migration finished with problems: {}", report);
        }
    }
}

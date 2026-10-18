package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Populates the modern schema from the legacy CDW tables at startup (toggle with {@code migration.run-on-startup}). */
@Component
@ConditionalOnProperty(name = "migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final DataMigrationService migrationService;
    private final boolean failOnError;

    public DataMigrationRunner(DataMigrationService migrationService,
                               @Value("${migration.fail-on-error:true}") boolean failOnError) {
        this.migrationService = migrationService;
        this.failOnError = failOnError;
    }

    @Override
    public void run(String... args) {
        MigrationReport report = migrationService.migrate();
        if (report.isSuccessful()) {
            log.info(report.summary());
            return;
        }
        log.error(report.summary());
        if (failOnError) {
            throw new IllegalStateException("Legacy -> modern migration failed validation; see log for details");
        }
    }
}

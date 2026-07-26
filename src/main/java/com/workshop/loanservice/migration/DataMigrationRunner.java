package com.workshop.loanservice.migration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy to modern data migration at startup so the application
 * always serves the modern schema. Disable with
 * {@code loanservice.migration.run-on-startup=false} once the modern tables
 * are populated by a real (external) migration job.
 */
@Component
public class DataMigrationRunner implements ApplicationRunner {

    private final DataMigrationService migrationService;
    private final boolean enabled;

    public DataMigrationRunner(DataMigrationService migrationService,
                               @Value("${loanservice.migration.run-on-startup:true}") boolean enabled) {
        this.migrationService = migrationService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            migrationService.migrate();
        }
    }
}

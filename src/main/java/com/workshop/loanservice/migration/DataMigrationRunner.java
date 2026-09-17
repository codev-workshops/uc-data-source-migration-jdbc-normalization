package com.workshop.loanservice.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy to modern migration once the schemas and legacy seed data
 * have been initialized. A failing migration fails application startup.
 */
@Component
public class DataMigrationRunner implements ApplicationRunner {

    private final DataMigrationService migrationService;

    public DataMigrationRunner(DataMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migrate();
    }
}

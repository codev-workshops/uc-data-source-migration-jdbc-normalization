package com.workshop.loanservice.migration;

import com.workshop.loanservice.service.DataMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final DataMigrationService dataMigrationService;

    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;

    public DataMigrationRunner(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    @Override
    public void run(String... args) {
        if (!migrationEnabled) {
            log.info("Data migration is disabled. Set migration.enabled=true to run.");
            return;
        }

        log.info("Data migration is enabled. Starting migration...");
        try {
            dataMigrationService.migrateAll();
            log.info("Data migration completed successfully.");
        } catch (Exception e) {
            log.error("Data migration failed: {}", e.getMessage(), e);
        }
    }
}

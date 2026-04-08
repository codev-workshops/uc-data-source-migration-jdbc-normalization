package com.workshop.loanservice.config;

import com.workshop.loanservice.dto.MigrationResult;
import com.workshop.loanservice.service.DataMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @deprecated Runtime migration no longer needed. Modern seed data is loaded directly via data-modern.sql. Scheduled for removal in Phase 6.
 */
@Deprecated
@Component
@ConditionalOnProperty(name = "app.use-modern-datasource", havingValue = "true")
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final DataMigrationService dataMigrationService;

    public DataMigrationRunner(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    @Override
    public void run(String... args) {
        MigrationResult result = dataMigrationService.migrate();
        log.info("Modern data source migration complete: {} borrowers, {} products, {} loans, {} payments",
                result.getBorrowersMigrated(), result.getProductsMigrated(),
                result.getLoansMigrated(), result.getPaymentsMigrated());
    }
}

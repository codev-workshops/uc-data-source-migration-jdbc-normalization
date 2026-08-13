package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates the modern tables from the legacy tables while the context starts up,
 * after {@code spring.sql.init} has created and seeded the schemas.
 * Disable with {@code loanservice.migration.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "loanservice.migration.enabled", havingValue = "true", matchIfMissing = true)
public class DataMigrationRunner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final DataMigrationService migrationService;

    public DataMigrationRunner(DataMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void afterPropertiesSet() {
        MigrationReport report = migrationService.migrate();
        log.info("Legacy to modern migration finished: {}", report);
        if (!report.isValid()) {
            throw new MigrationDataException("Migration validation failed: " + report.getValidationFailures());
        }
    }
}

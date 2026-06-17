package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true", matchIfMissing = false)
public class DataMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);
    private static final String MIGRATION_NAME = "legacy_to_modern_v1";

    private final JdbcTemplate jdbcTemplate;

    public DataMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("DataMigrationRunner started");

        createMigrationLogTable();

        if (isMigrationCompleted()) {
            log.info("Migration '{}' already completed. Skipping.", MIGRATION_NAME);
            return;
        }

        markMigrationRunning();

        try {
            executeSchemaCreation();
            cleanModernTables();
            executeDataMigration();
            markMigrationCompleted();
            log.info("Migration '{}' completed successfully.", MIGRATION_NAME);
        } catch (Exception e) {
            markMigrationFailed();
            log.error("Migration '{}' failed.", MIGRATION_NAME, e);
            throw new RuntimeException("Data migration failed", e);
        }
    }

    private void createMigrationLogTable() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS migration_log (" +
            "    id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "    migration_name VARCHAR(100) UNIQUE NOT NULL," +
            "    status VARCHAR(20) NOT NULL," +
            "    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    completed_at TIMESTAMP" +
            ")"
        );
    }

    private boolean isMigrationCompleted() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT status FROM migration_log WHERE migration_name = ?",
            MIGRATION_NAME
        );
        if (rows.isEmpty()) {
            return false;
        }
        return "COMPLETED".equals(rows.get(0).get("status"));
    }

    private void markMigrationRunning() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id FROM migration_log WHERE migration_name = ?",
            MIGRATION_NAME
        );
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO migration_log (migration_name, status, started_at) VALUES (?, 'RUNNING', ?)",
                MIGRATION_NAME, LocalDateTime.now()
            );
        } else {
            jdbcTemplate.update(
                "UPDATE migration_log SET status = 'RUNNING', started_at = ? WHERE migration_name = ?",
                LocalDateTime.now(), MIGRATION_NAME
            );
        }
    }

    private void executeSchemaCreation() {
        log.info("Creating modern schema tables...");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-modern.sql"));
        populator.setSeparator(";");
        populator.execute(jdbcTemplate.getDataSource());
    }

    @Transactional
    private void cleanModernTables() {
        log.info("Cleaning modern tables for retry safety...");
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM loan_accounts");
        jdbcTemplate.execute("DELETE FROM loan_products");
        jdbcTemplate.execute("DELETE FROM borrowers");
    }

    @Transactional
    private void executeDataMigration() {
        log.info("Executing data migration from legacy to modern tables...");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("data-migration.sql"));
        populator.setSeparator(";");
        populator.execute(jdbcTemplate.getDataSource());
    }

    private void markMigrationCompleted() {
        jdbcTemplate.update(
            "UPDATE migration_log SET status = 'COMPLETED', completed_at = ? WHERE migration_name = ?",
            LocalDateTime.now(), MIGRATION_NAME
        );
    }

    private void markMigrationFailed() {
        jdbcTemplate.update(
            "UPDATE migration_log SET status = 'FAILED' WHERE migration_name = ?",
            MIGRATION_NAME
        );
    }
}

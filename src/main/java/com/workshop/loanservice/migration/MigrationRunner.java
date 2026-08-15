package com.workshop.loanservice.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy → modern migration during context startup.
 *
 * <p>The migration deliberately runs in {@code @PostConstruct} rather than from an
 * {@code ApplicationRunner}/{@code ApplicationReadyEvent} listener: runners fire after the web
 * server has started accepting requests, which would let the API serve an empty modern
 * schema for a short window. Depending on the modern repositories (and therefore, transitively,
 * on the legacy entity manager and its {@code spring.sql.init} seeding) guarantees both data
 * sources are ready before this bean is initialized.
 */
@Component
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final DataMigrationService dataMigrationService;

    public MigrationRunner(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    @PostConstruct
    public void migrateOnStartup() {
        log.info("Starting legacy → modern data migration");
        dataMigrationService.migrateAll();
    }
}

package com.workshop.loanservice.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy → modern load at startup when {@code migration.run-on-startup=true}. Off by
 * default; enabling it requires {@code schema-modern.sql} and {@code data-modern-reference.sql} in
 * the SQL init scripts.
 */
@Component
@ConditionalOnProperty(name = "migration.run-on-startup", havingValue = "true")
public class MigrationRunner implements ApplicationRunner {

  private final LegacyToModernMigrator migrator;

  public MigrationRunner(LegacyToModernMigrator migrator) {
    this.migrator = migrator;
  }

  @Override
  public void run(ApplicationArguments args) {
    migrator.migrate();
  }
}

package com.workshop.loanservice.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy → modern migration and reconciliation at startup. Startup fails if the
 * modern tables do not reconcile with the legacy source.
 */
@Component
@ConditionalOnProperty(name = "loanservice.migration.enabled", havingValue = "true",
    matchIfMissing = true)
public class MigrationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

  private final DataMigrationService migrationService;
  private final MigrationReconciliationService reconciliationService;

  public MigrationRunner(DataMigrationService migrationService,
      MigrationReconciliationService reconciliationService) {
    this.migrationService = migrationService;
    this.reconciliationService = reconciliationService;
  }

  @Override
  public void run(ApplicationArguments args) {
    migrationService.migrate();
    ReconciliationReport reconciliation = reconciliationService.reconcile();
    if (!reconciliation.passed()) {
      throw new IllegalStateException(reconciliation.summary());
    }
    log.info(reconciliation.summary());
  }
}

package com.workshop.loanservice.migration;

import com.workshop.loanservice.config.MigrationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Runs the backfill at startup when {@code loanservice.migration.run-on-startup} is set.
 *
 * <p>The run is synchronous and, in strict mode, fatal: if the modern store cannot be populated
 * correctly there is no point serving reads from it. Publishing the outcome as gauges lets a cutover
 * be verified from the metrics endpoint instead of by reading logs.
 */
@Component
@ConditionalOnProperty(name = "loanservice.migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationRunner implements ApplicationRunner {

    private final LegacyToModernMigrationService migrationService;
    private final ReconciliationService reconciliation;
    private final MeterRegistry meterRegistry;
    private final MigrationProperties properties;
    private final ApplicationContext context;

    public MigrationRunner(LegacyToModernMigrationService migrationService,
                           ReconciliationService reconciliation,
                           MeterRegistry meterRegistry,
                           MigrationProperties properties,
                           ApplicationContext context) {
        this.migrationService = migrationService;
        this.reconciliation = reconciliation;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationReport report = migrationService.migrate();
        gauge(report, "written", MigrationReport::totalWritten);
        gauge(report, "skipped", MigrationReport::totalSkipped);
        gauge(report, "rejected", r -> r.getRejections().size());
        meterRegistry.gauge("loanservice.migration.duration.seconds", report,
            r -> r.getDuration().toMillis() / 1000.0);
        // Publishes per-table drift immediately: the backfill is only finished when it reconciles.
        reconciliation.reconcile();

        if (properties.isExitAfterMigration()) {
            // One-shot job mode: the backfill is the whole point of this process, so it stops rather
            // than sitting there serving an API nobody asked it for.
            SpringApplication.exit(context, () -> 0);
        }
    }

    private void gauge(MigrationReport report, String outcome,
                       ToDoubleFunction<MigrationReport> value) {
        meterRegistry.gauge("loanservice.migration.rows", List.of(Tag.of("outcome", outcome)), report, value);
    }
}

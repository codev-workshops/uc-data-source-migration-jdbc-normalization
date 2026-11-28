package com.workshop.loanservice.migration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the legacy-to-modern migration and reconciliation during startup.
 *
 * <p>It runs once all singletons exist and before the embedded web server starts
 * accepting connections, so no request is served from a partially migrated schema.
 * A failure or reconciliation mismatch is recorded in {@link MigrationStatus} and reported as DOWN
 * by the {@code migration} health indicator (part of the readiness group). With
 * {@code loan.migration.fail-on-error=true} it also aborts startup.
 *
 * <p>Metrics: {@code loan.migration.rows} (counter, tags {@code table}, {@code outcome}),
 * {@code loan.migration.duration} (timer, tag {@code outcome=success|failure}) and the gauges
 * {@code loan.migration.reconciliation.checks} / {@code loan.migration.reconciliation.mismatches}.
 */
@Component
@ConditionalOnProperty(name = "loan.migration.run-on-startup", havingValue = "true", matchIfMissing = true)
public class MigrationRunner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyToModernMigrator migrator;
    private final MigrationStatus status;
    private final MeterRegistry registry;
    private final boolean failOnError;

    public MigrationRunner(LegacyToModernMigrator migrator, MigrationStatus status, MeterRegistry registry,
                           @Value("${loan.migration.fail-on-error:false}") boolean failOnError) {
        this.migrator = migrator;
        this.status = status;
        this.registry = registry;
        this.failOnError = failOnError;
        Gauge.builder("loan.migration.reconciliation.checks", status, s -> s.getChecks().size())
                .description("Reconciliation checks run after the startup migration").register(registry);
        Gauge.builder("loan.migration.reconciliation.mismatches", status, s -> s.getMismatches().size())
                .description("Reconciliation checks that found a MISMATCH").register(registry);
    }

    @Override
    public void afterSingletonsInstantiated() {
        status.started();
        long start = System.nanoTime();
        try {
            MigrationReport report = migrator.migrate();
            log.info("Legacy-to-modern migration finished in {} ms:{}", elapsedMillis(start), report);
            recordRows(report);

            List<ReconciliationCheck> checks = migrator.reconcile(report);
            checks.forEach(check -> {
                if (check.passed()) {
                    log.info("Reconciliation {}", check);
                } else {
                    log.warn("Reconciliation {}", check);
                }
            });
            status.completed(report, checks);
            recordDuration(start, "success");
        } catch (RuntimeException e) {
            recordDuration(start, "failure");
            log.error("Legacy-to-modern migration failed after {} ms", elapsedMillis(start), e);
            status.failed(e);
            if (failOnError) {
                throw new IllegalStateException("Legacy-to-modern migration failed", e);
            }
            return;
        }

        List<ReconciliationCheck> mismatches = status.getMismatches();
        if (mismatches.isEmpty()) {
            log.info("Reconciliation passed: {} checks; total migration time {} ms",
                    status.getChecks().size(), elapsedMillis(start));
            return;
        }
        log.error("Reconciliation found {} MISMATCH(es) out of {} checks: {}",
                mismatches.size(), status.getChecks().size(), mismatches);
        if (failOnError) {
            throw new IllegalStateException("Reconciliation found " + mismatches.size() + " MISMATCH(es): " + mismatches);
        }
    }

    private void recordRows(MigrationReport report) {
        report.getTables().forEach((table, t) -> {
            rows(table, "read").increment(t.getRead());
            rows(table, "inserted").increment(t.getInserted());
            rows(table, "skipped_existing").increment(t.getSkippedExisting());
            rows(table, "rejected").increment(t.getRejections().size());
            rows(table, "duplicate").increment(t.getDuplicates().size());
        });
    }

    private Counter rows(String table, String outcome) {
        return Counter.builder("loan.migration.rows").description("Legacy rows processed by the startup migration")
                .tag("table", table).tag("outcome", outcome).register(registry);
    }

    private void recordDuration(long start, String outcome) {
        Timer.builder("loan.migration.duration").description("Startup migration and reconciliation time")
                .tag("outcome", outcome).register(registry).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}

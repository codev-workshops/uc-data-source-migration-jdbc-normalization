package com.workshop.loanservice.migration;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outcome of the startup migration, read by {@link MigrationHealthIndicator}.
 */
@Component
public class MigrationStatus {

    public enum State { NOT_RUN, RUNNING, COMPLETED, FAILED }

    private volatile State state = State.NOT_RUN;
    private volatile MigrationReport report;
    private volatile List<ReconciliationCheck> checks = List.of();
    private volatile String error;

    void started() {
        state = State.RUNNING;
    }

    void completed(MigrationReport report, List<ReconciliationCheck> checks) {
        this.report = report;
        this.checks = List.copyOf(checks);
        this.state = State.COMPLETED;
    }

    void failed(Throwable e) {
        this.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        this.state = State.FAILED;
    }

    public State getState() { return state; }
    public MigrationReport getReport() { return report; }
    public List<ReconciliationCheck> getChecks() { return checks; }
    public String getError() { return error; }

    public List<ReconciliationCheck> getMismatches() {
        return checks.stream().filter(check -> !check.passed()).toList();
    }
}

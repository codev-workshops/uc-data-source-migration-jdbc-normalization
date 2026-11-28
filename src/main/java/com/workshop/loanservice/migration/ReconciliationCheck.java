package com.workshop.loanservice.migration;

/**
 * One legacy-vs-modern comparison (row count, amount total or FK mapping).
 */
public record ReconciliationCheck(String name, String legacyValue, String modernValue, boolean passed) {

    @Override
    public String toString() {
        return String.format("[%s] %s legacy=%s modern=%s", passed ? "OK" : "MISMATCH", name, legacyValue, modernValue);
    }
}

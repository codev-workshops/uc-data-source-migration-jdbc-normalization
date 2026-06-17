package com.workshop.loanservice.migration;

/**
 * Summary of a migration run: how many rows of each entity were written to the
 * modern schema.
 */
public record MigrationResult(
        long borrowers,
        long loanProducts,
        long loanAccounts,
        long payments) {
}

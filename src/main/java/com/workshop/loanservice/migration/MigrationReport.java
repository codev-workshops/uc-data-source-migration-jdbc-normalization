package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.List;

/** Outcome of a migration run: per-table counts plus anything that was skipped or mismatched. */
public class MigrationReport {

    private int borrowersMigrated;
    private int productsMigrated;
    private int loanAccountsMigrated;
    private int paymentsMigrated;
    private final List<String> skipped = new ArrayList<>();
    private final List<String> validationFailures = new ArrayList<>();

    public int getBorrowersMigrated() { return borrowersMigrated; }
    public int getProductsMigrated() { return productsMigrated; }
    public int getLoanAccountsMigrated() { return loanAccountsMigrated; }
    public int getPaymentsMigrated() { return paymentsMigrated; }
    public List<String> getSkipped() { return skipped; }
    public List<String> getValidationFailures() { return validationFailures; }

    void borrowerMigrated() { borrowersMigrated++; }
    void productMigrated() { productsMigrated++; }
    void loanAccountMigrated() { loanAccountsMigrated++; }
    void paymentMigrated() { paymentsMigrated++; }

    void skip(String reason) { skipped.add(reason); }

    void validationFailure(String reason) { validationFailures.add(reason); }

    public boolean isValid() { return validationFailures.isEmpty(); }

    @Override
    public String toString() {
        return "MigrationReport{borrowers=" + borrowersMigrated
                + ", products=" + productsMigrated
                + ", loanAccounts=" + loanAccountsMigrated
                + ", payments=" + paymentsMigrated
                + ", skipped=" + skipped
                + ", validationFailures=" + validationFailures + "}";
    }
}

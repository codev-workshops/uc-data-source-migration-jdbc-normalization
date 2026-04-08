package com.workshop.loanservice.dto;

/**
 * @deprecated Used only by DataMigrationService which is no longer needed. Scheduled for removal in Phase 6.
 */
@Deprecated
public class MigrationResult {

    private int borrowersMigrated;
    private int productsMigrated;
    private int loansMigrated;
    private int paymentsMigrated;

    public MigrationResult() {
    }

    public MigrationResult(int borrowersMigrated, int productsMigrated, int loansMigrated, int paymentsMigrated) {
        this.borrowersMigrated = borrowersMigrated;
        this.productsMigrated = productsMigrated;
        this.loansMigrated = loansMigrated;
        this.paymentsMigrated = paymentsMigrated;
    }

    public int getBorrowersMigrated() { return borrowersMigrated; }
    public void setBorrowersMigrated(int borrowersMigrated) { this.borrowersMigrated = borrowersMigrated; }
    public int getProductsMigrated() { return productsMigrated; }
    public void setProductsMigrated(int productsMigrated) { this.productsMigrated = productsMigrated; }
    public int getLoansMigrated() { return loansMigrated; }
    public void setLoansMigrated(int loansMigrated) { this.loansMigrated = loansMigrated; }
    public int getPaymentsMigrated() { return paymentsMigrated; }
    public void setPaymentsMigrated(int paymentsMigrated) { this.paymentsMigrated = paymentsMigrated; }
}

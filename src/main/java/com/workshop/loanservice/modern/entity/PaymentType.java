package com.workshop.loanservice.modern.entity;

/** Payment type stored in {@code payments.type}. The label is the text the API returns. */
public enum PaymentType {
    REGULAR("REG", "Regular"),
    EXTRA("EXT", "Extra"),
    PARTIAL("PRT", "Partial"),
    PREPAYMENT("PRE", "Prepayment");

    private final String legacyCode;
    private final String label;

    PaymentType(String legacyCode, String label) {
        this.legacyCode = legacyCode;
        this.label = label;
    }

    public String getLegacyCode() {
        return legacyCode;
    }

    public String getLabel() {
        return label;
    }
}

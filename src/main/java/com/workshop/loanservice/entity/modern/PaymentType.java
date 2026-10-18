package com.workshop.loanservice.entity.modern;

/** Kind of payment, stored by name in {@code payments.type}. */
public enum PaymentType {
    REGULAR("Regular"),
    EXTRA("Extra"),
    PARTIAL("Partial"),
    PREPAYMENT("Prepayment");

    private final String displayName;

    PaymentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

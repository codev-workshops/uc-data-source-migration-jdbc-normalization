package com.workshop.loanservice.modern.entity;

/** Payment type stored in {@code payments.type}. The label is the text the API returns. */
public enum PaymentType {
    REGULAR("Regular"),
    EXTRA("Extra"),
    PARTIAL("Partial"),
    PREPAYMENT("Prepayment");

    private final String label;

    PaymentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

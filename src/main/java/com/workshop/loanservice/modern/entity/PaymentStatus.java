package com.workshop.loanservice.modern.entity;

/** Payment status stored in {@code payments.status}. The label is the text the API returns. */
public enum PaymentStatus {
    POSTED("PST", "Posted"),
    REVERSED("REV", "Reversed"),
    NSF("NSF", "Non-Sufficient Funds"),
    PENDING("PND", "Pending");

    private final String legacyCode;
    private final String label;

    PaymentStatus(String legacyCode, String label) {
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

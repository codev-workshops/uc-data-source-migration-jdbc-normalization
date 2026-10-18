package com.workshop.loanservice.entity.modern;

/** Processing status of a payment, stored by name in {@code payments.status}. */
public enum PaymentStatus {
    POSTED("Posted"),
    REVERSED("Reversed"),
    NSF("Non-Sufficient Funds"),
    PENDING("Pending");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package com.workshop.loanservice.service;

/** A payment could not be posted. Carries a fixed reason, never caller-supplied text. */
public class PaymentPostingException extends RuntimeException {

    public PaymentPostingException(String reason) {
        super(reason);
    }
}

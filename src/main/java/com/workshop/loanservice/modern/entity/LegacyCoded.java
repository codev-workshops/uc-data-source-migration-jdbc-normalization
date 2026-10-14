package com.workshop.loanservice.modern.entity;

/** A modern enum value that was stored as a short code (e.g. {@code ACT}) in the CDW tables. */
public interface LegacyCoded {

    String getLegacyCode();
}

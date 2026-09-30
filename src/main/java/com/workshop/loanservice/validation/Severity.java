package com.workshop.loanservice.validation;

/**
 * Severity of a {@link ValidationFinding}.
 * ERROR marks data that cannot be migrated as-is; WARNING marks suspicious but loadable data.
 */
public enum Severity {
    ERROR,
    WARNING
}

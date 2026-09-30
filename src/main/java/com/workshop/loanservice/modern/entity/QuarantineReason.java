package com.workshop.loanservice.modern.entity;

/** Why the migration loader rejected a legacy row (column {@code migration_quarantine.reason_code}). */
public enum QuarantineReason {
  /** A required legacy value is NULL or blank. */
  MISSING_REQUIRED,
  /** A value could not be parsed into its target type (date, amount, integer). */
  MALFORMED_VALUE,
  /** A code does not exist in its reference table. */
  UNKNOWN_CODE,
  /** A legacy foreign key (borrower, product, loan account) has no migrated parent. */
  ORPHAN_REFERENCE,
  /** Values that must agree across legacy rows disagree (e.g. SSN last-4 across loans). */
  CONFLICTING_VALUE,
  /** Payment total differs from principal + interest + escrow ({@code ck_payment_split}). */
  PAYMENT_SPLIT_MISMATCH,
  /** The target schema rejected the row with a constraint the loader does not pre-check. */
  CONSTRAINT_VIOLATION
}

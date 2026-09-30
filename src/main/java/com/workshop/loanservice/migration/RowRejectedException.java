package com.workshop.loanservice.migration;

import com.workshop.loanservice.modern.entity.QuarantineReason;

/** Thrown while mapping a legacy row that must be quarantined instead of loaded. */
public class RowRejectedException extends RuntimeException {

  private final QuarantineReason reason;
  private final String field;

  public RowRejectedException(QuarantineReason reason, String field, String detail) {
    super(detail);
    this.reason = reason;
    this.field = field;
  }

  public QuarantineReason getReason() {
    return reason;
  }

  public String getField() {
    return field;
  }
}

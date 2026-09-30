package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Loan status reference code, e.g. ACT = Active (table {@code loan_status}). */
@Entity
@Immutable
@Table(name = "loan_status")
public class LoanStatus extends ReferenceCode {

  @Column(name = "is_open", nullable = false)
  private boolean open;

  protected LoanStatus() {}

  public boolean isOpen() {
    return open;
  }
}

package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Payment status reference code, e.g. PST = Posted (table {@code payment_status}). */
@Entity
@Immutable
@Table(name = "payment_status")
public class PaymentStatus extends ReferenceCode {

  @Column(name = "is_final", nullable = false)
  private boolean finalStatus;

  protected PaymentStatus() {}

  public boolean isFinalStatus() {
    return finalStatus;
  }
}

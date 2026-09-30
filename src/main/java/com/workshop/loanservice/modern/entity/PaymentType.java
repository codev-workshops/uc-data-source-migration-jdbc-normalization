package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Payment type reference code, e.g. REG = Regular (table {@code payment_type}). */
@Entity
@Immutable
@Table(name = "payment_type")
public class PaymentType extends ReferenceCode {

  protected PaymentType() {}
}

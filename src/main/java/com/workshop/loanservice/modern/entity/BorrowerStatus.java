package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Borrower status reference code, e.g. ACT = Active (table {@code borrower_status}). */
@Entity
@Immutable
@Table(name = "borrower_status")
public class BorrowerStatus extends ReferenceCode {

  protected BorrowerStatus() {}
}

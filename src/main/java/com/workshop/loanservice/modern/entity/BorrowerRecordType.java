package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Borrower record type reference code, e.g. PRI = Primary (table {@code borrower_record_type}). */
@Entity
@Immutable
@Table(name = "borrower_record_type")
public class BorrowerRecordType extends ReferenceCode {

  protected BorrowerRecordType() {}
}

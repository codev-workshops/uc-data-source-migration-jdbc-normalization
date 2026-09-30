package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Loan product type reference code, e.g. FXD = Fixed Rate (table {@code product_type}). */
@Entity
@Immutable
@Table(name = "product_type")
public class ProductType extends ReferenceCode {

  protected ProductType() {}
}

package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Interest rate type reference code, e.g. FIXED (table {@code rate_type}). */
@Entity
@Immutable
@Table(name = "rate_type")
public class RateType extends ReferenceCode {

  protected RateType() {}
}

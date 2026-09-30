package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Employment status reference code, e.g. SELF_EMPLOYED (table {@code employment_status}). */
@Entity
@Immutable
@Table(name = "employment_status")
public class EmploymentStatus extends ReferenceCode {

  protected EmploymentStatus() {}
}

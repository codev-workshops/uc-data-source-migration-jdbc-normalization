package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Property type reference code, e.g. SFR = Single Family Residence (table {@code property_type}). */
@Entity
@Immutable
@Table(name = "property_type")
public class PropertyType extends ReferenceCode {

  protected PropertyType() {}
}

package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Common shape of the read-only code/label reference tables. */
@MappedSuperclass
public abstract class ReferenceCode {

  @Id
  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "label", nullable = false, unique = true)
  private String label;

  protected ReferenceCode() {}

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }
}

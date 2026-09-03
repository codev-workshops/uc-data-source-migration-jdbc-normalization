package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference table {@code employment_status}.
 */
@Entity
@Table(name = "employment_status")
public class EmploymentStatus {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}

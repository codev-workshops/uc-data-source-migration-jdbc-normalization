package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference table {@code loan_status}.
 */
@Entity
@Table(name = "loan_status")
public class LoanStatus {

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "is_open", nullable = false)
    private Boolean isOpen;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Boolean getIsOpen() { return isOpen; }
    public void setIsOpen(Boolean isOpen) { this.isOpen = isOpen; }
}

package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference table {@code payment_status}.
 */
@Entity
@Table(name = "payment_status")
public class PaymentStatus {

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "is_final", nullable = false)
    private Boolean isFinal;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Boolean getIsFinal() { return isFinal; }
    public void setIsFinal(Boolean isFinal) { this.isFinal = isFinal; }
}

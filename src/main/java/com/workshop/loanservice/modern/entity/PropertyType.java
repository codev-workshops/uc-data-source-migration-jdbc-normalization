package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reference table {@code property_type}.
 */
@Entity
@Table(name = "property_type")
public class PropertyType {

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}

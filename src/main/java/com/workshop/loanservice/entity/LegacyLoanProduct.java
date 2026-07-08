package com.workshop.loanservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the legacy CDW_LN_PROD table.
 *
 * @deprecated the modern schema is the operative data source; use
 * {@link com.workshop.loanservice.modern.entity.LoanProduct} instead.
 * Retained only for the dual-read fallback ({@code datasource.mode=legacy})
 * and as the migration source.
 */
@Deprecated
@Entity
@Table(name = "CDW_LN_PROD")
public class LegacyLoanProduct {

    @Id
    @Column(name = "PROD_CD")
    private String productCode;

    @Column(name = "PROD_DESC_TXT")
    private String description;

    @Column(name = "PROD_TYP_CD")
    private String typeCode;

    @Column(name = "PROD_TERM_MOS")
    private String termMonths;

    @Column(name = "PROD_RT_TYP")
    private String rateType;

    @Column(name = "PROD_MIN_AMT")
    private String minAmount;

    @Column(name = "PROD_MAX_AMT")
    private String maxAmount;

    @Column(name = "PROD_STAT_CD")
    private String statusCode;

    @Column(name = "PROD_EFF_DT")
    private String effectiveDate;

    @Column(name = "PROD_EXP_DT")
    private String expirationDate;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getTermMonths() { return termMonths; }
    public void setTermMonths(String termMonths) { this.termMonths = termMonths; }
    public String getRateType() { return rateType; }
    public void setRateType(String rateType) { this.rateType = rateType; }
    public String getMinAmount() { return minAmount; }
    public void setMinAmount(String minAmount) { this.minAmount = minAmount; }
    public String getMaxAmount() { return maxAmount; }
    public void setMaxAmount(String maxAmount) { this.maxAmount = maxAmount; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
}

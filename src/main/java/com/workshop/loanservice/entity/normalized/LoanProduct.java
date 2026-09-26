package com.workshop.loanservice.entity.normalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Maps to the normalized {@code loan_product} table created by Flyway. */
@Entity
@Table(name = "loan_product")
public class LoanProduct {

  @Id
  @Column(name = "product_code")
  private String productCode;

  @Column(name = "description")
  private String description;

  @Column(name = "product_type")
  private String productType;

  @Column(name = "term_months")
  private Integer termMonths;

  @Column(name = "rate_type")
  private String rateType;

  @Column(name = "min_amount")
  private BigDecimal minAmount;

  @Column(name = "max_amount")
  private BigDecimal maxAmount;

  @Column(name = "status_code")
  private String statusCode;

  @Column(name = "effective_date")
  private LocalDate effectiveDate;

  @Column(name = "expiration_date")
  private LocalDate expirationDate;

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getProductType() {
    return productType;
  }

  public void setProductType(String productType) {
    this.productType = productType;
  }

  public Integer getTermMonths() {
    return termMonths;
  }

  public void setTermMonths(Integer termMonths) {
    this.termMonths = termMonths;
  }

  public String getRateType() {
    return rateType;
  }

  public void setRateType(String rateType) {
    this.rateType = rateType;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public LocalDate getExpirationDate() {
    return expirationDate;
  }

  public void setExpirationDate(LocalDate expirationDate) {
    this.expirationDate = expirationDate;
  }
}

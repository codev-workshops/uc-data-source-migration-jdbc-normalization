package com.workshop.loanservice.entity.normalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps to the normalized {@code loan_account} table created by Flyway.
 *
 * <p>Borrower details are no longer embedded in the loan row; they are reached through the
 * {@code borrower_id} association.
 */
@Entity
@Table(name = "loan_account")
public class LoanAccount {

  @Id
  @Column(name = "loan_account_number")
  private String loanAccountNumber;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "borrower_id")
  private Borrower borrower;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "product_code")
  private LoanProduct loanProduct;

  @Column(name = "product_code", insertable = false, updatable = false)
  private String productCode;

  @Column(name = "original_amount")
  private BigDecimal originalAmount;

  @Column(name = "current_balance")
  private BigDecimal currentBalance;

  @Column(name = "interest_rate")
  private BigDecimal interestRate;

  @Column(name = "term_months")
  private Integer termMonths;

  @Column(name = "monthly_payment")
  private BigDecimal monthlyPayment;

  @Column(name = "origination_date")
  private LocalDate originationDate;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Column(name = "first_payment_date")
  private LocalDate firstPaymentDate;

  @Column(name = "next_payment_date")
  private LocalDate nextPaymentDate;

  @Column(name = "status_code")
  private String statusCode;

  @Column(name = "delinquent_days")
  private Integer delinquentDays;

  @Column(name = "escrow_balance")
  private BigDecimal escrowBalance;

  @Column(name = "ltv_percent")
  private BigDecimal ltvPercent;

  @Column(name = "property_address_line1")
  private String propertyAddressLine1;

  @Column(name = "property_city")
  private String propertyCity;

  @Column(name = "property_state")
  private String propertyState;

  @Column(name = "property_zip")
  private String propertyZip;

  @Column(name = "property_type")
  private String propertyType;

  @Column(name = "property_appraised_value")
  private BigDecimal propertyAppraisedValue;

  @Column(name = "created_date")
  private LocalDate createdDate;

  @Column(name = "updated_date")
  private LocalDate updatedDate;

  public String getLoanAccountNumber() {
    return loanAccountNumber;
  }

  public void setLoanAccountNumber(String loanAccountNumber) {
    this.loanAccountNumber = loanAccountNumber;
  }

  public Borrower getBorrower() {
    return borrower;
  }

  public void setBorrower(Borrower borrower) {
    this.borrower = borrower;
  }

  public LoanProduct getLoanProduct() {
    return loanProduct;
  }

  public void setLoanProduct(LoanProduct loanProduct) {
    this.loanProduct = loanProduct;
  }

  public String getProductCode() {
    return productCode;
  }

  public BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  public void setOriginalAmount(BigDecimal originalAmount) {
    this.originalAmount = originalAmount;
  }

  public BigDecimal getCurrentBalance() {
    return currentBalance;
  }

  public void setCurrentBalance(BigDecimal currentBalance) {
    this.currentBalance = currentBalance;
  }

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public void setInterestRate(BigDecimal interestRate) {
    this.interestRate = interestRate;
  }

  public Integer getTermMonths() {
    return termMonths;
  }

  public void setTermMonths(Integer termMonths) {
    this.termMonths = termMonths;
  }

  public BigDecimal getMonthlyPayment() {
    return monthlyPayment;
  }

  public void setMonthlyPayment(BigDecimal monthlyPayment) {
    this.monthlyPayment = monthlyPayment;
  }

  public LocalDate getOriginationDate() {
    return originationDate;
  }

  public void setOriginationDate(LocalDate originationDate) {
    this.originationDate = originationDate;
  }

  public LocalDate getMaturityDate() {
    return maturityDate;
  }

  public void setMaturityDate(LocalDate maturityDate) {
    this.maturityDate = maturityDate;
  }

  public LocalDate getFirstPaymentDate() {
    return firstPaymentDate;
  }

  public void setFirstPaymentDate(LocalDate firstPaymentDate) {
    this.firstPaymentDate = firstPaymentDate;
  }

  public LocalDate getNextPaymentDate() {
    return nextPaymentDate;
  }

  public void setNextPaymentDate(LocalDate nextPaymentDate) {
    this.nextPaymentDate = nextPaymentDate;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public Integer getDelinquentDays() {
    return delinquentDays;
  }

  public void setDelinquentDays(Integer delinquentDays) {
    this.delinquentDays = delinquentDays;
  }

  public BigDecimal getEscrowBalance() {
    return escrowBalance;
  }

  public void setEscrowBalance(BigDecimal escrowBalance) {
    this.escrowBalance = escrowBalance;
  }

  public BigDecimal getLtvPercent() {
    return ltvPercent;
  }

  public void setLtvPercent(BigDecimal ltvPercent) {
    this.ltvPercent = ltvPercent;
  }

  public String getPropertyAddressLine1() {
    return propertyAddressLine1;
  }

  public void setPropertyAddressLine1(String propertyAddressLine1) {
    this.propertyAddressLine1 = propertyAddressLine1;
  }

  public String getPropertyCity() {
    return propertyCity;
  }

  public void setPropertyCity(String propertyCity) {
    this.propertyCity = propertyCity;
  }

  public String getPropertyState() {
    return propertyState;
  }

  public void setPropertyState(String propertyState) {
    this.propertyState = propertyState;
  }

  public String getPropertyZip() {
    return propertyZip;
  }

  public void setPropertyZip(String propertyZip) {
    this.propertyZip = propertyZip;
  }

  public String getPropertyType() {
    return propertyType;
  }

  public void setPropertyType(String propertyType) {
    this.propertyType = propertyType;
  }

  public BigDecimal getPropertyAppraisedValue() {
    return propertyAppraisedValue;
  }

  public void setPropertyAppraisedValue(BigDecimal propertyAppraisedValue) {
    this.propertyAppraisedValue = propertyAppraisedValue;
  }

  public LocalDate getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(LocalDate createdDate) {
    this.createdDate = createdDate;
  }

  public LocalDate getUpdatedDate() {
    return updatedDate;
  }

  public void setUpdatedDate(LocalDate updatedDate) {
    this.updatedDate = updatedDate;
  }
}

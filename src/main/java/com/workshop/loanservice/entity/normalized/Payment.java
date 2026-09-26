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

/** Maps to the normalized {@code payment} table created by Flyway. */
@Entity
@Table(name = "payment")
public class Payment {

  @Id
  @Column(name = "payment_id")
  private String paymentId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "loan_account_number")
  private LoanAccount loanAccount;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(name = "principal_amount")
  private BigDecimal principalAmount;

  @Column(name = "interest_amount")
  private BigDecimal interestAmount;

  @Column(name = "escrow_amount")
  private BigDecimal escrowAmount;

  @Column(name = "late_fee")
  private BigDecimal lateFee;

  @Column(name = "type_code")
  private String typeCode;

  @Column(name = "status_code")
  private String statusCode;

  @Column(name = "received_date")
  private LocalDate receivedDate;

  @Column(name = "processed_date")
  private LocalDate processedDate;

  @Column(name = "created_date")
  private LocalDate createdDate;

  @Column(name = "updated_date")
  private LocalDate updatedDate;

  public String getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }

  public LoanAccount getLoanAccount() {
    return loanAccount;
  }

  public void setLoanAccount(LoanAccount loanAccount) {
    this.loanAccount = loanAccount;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public void setPaymentDate(LocalDate paymentDate) {
    this.paymentDate = paymentDate;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public BigDecimal getPrincipalAmount() {
    return principalAmount;
  }

  public void setPrincipalAmount(BigDecimal principalAmount) {
    this.principalAmount = principalAmount;
  }

  public BigDecimal getInterestAmount() {
    return interestAmount;
  }

  public void setInterestAmount(BigDecimal interestAmount) {
    this.interestAmount = interestAmount;
  }

  public BigDecimal getEscrowAmount() {
    return escrowAmount;
  }

  public void setEscrowAmount(BigDecimal escrowAmount) {
    this.escrowAmount = escrowAmount;
  }

  public BigDecimal getLateFee() {
    return lateFee;
  }

  public void setLateFee(BigDecimal lateFee) {
    this.lateFee = lateFee;
  }

  public String getTypeCode() {
    return typeCode;
  }

  public void setTypeCode(String typeCode) {
    this.typeCode = typeCode;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }

  public LocalDate getReceivedDate() {
    return receivedDate;
  }

  public void setReceivedDate(LocalDate receivedDate) {
    this.receivedDate = receivedDate;
  }

  public LocalDate getProcessedDate() {
    return processedDate;
  }

  public void setProcessedDate(LocalDate processedDate) {
    this.processedDate = processedDate;
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

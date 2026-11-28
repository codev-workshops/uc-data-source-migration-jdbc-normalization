package com.workshop.loanservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment DTO for API responses.
 */
public class PaymentDto {

    private String paymentId;
    private String loanAccountNumber;
    @JsonFormat(pattern = "MM/dd/yyyy")
    private LocalDate paymentDate;
    private BigDecimal totalAmount;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal escrowAmount;
    private BigDecimal lateFee;
    private String type;
    private String status;

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getLoanAccountNumber() { return loanAccountNumber; }
    public void setLoanAccountNumber(String loanAccountNumber) { this.loanAccountNumber = loanAccountNumber; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }
    public BigDecimal getEscrowAmount() { return escrowAmount; }
    public void setEscrowAmount(BigDecimal escrowAmount) { this.escrowAmount = escrowAmount; }
    public BigDecimal getLateFee() { return lateFee; }
    public void setLateFee(BigDecimal lateFee) { this.lateFee = lateFee; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

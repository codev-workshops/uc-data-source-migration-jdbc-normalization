package com.workshop.borrower.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Projection of a loan summary returned by loan-service. Only the fields
 * relevant to a borrower's loan list are captured; unknown fields are ignored
 * so the two services can evolve independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BorrowerLoanDto {

    private String loanAccountNumber;
    private String productDescription;
    private BigDecimal currentBalance;
    private String status;

    public String getLoanAccountNumber() { return loanAccountNumber; }
    public void setLoanAccountNumber(String loanAccountNumber) { this.loanAccountNumber = loanAccountNumber; }
    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

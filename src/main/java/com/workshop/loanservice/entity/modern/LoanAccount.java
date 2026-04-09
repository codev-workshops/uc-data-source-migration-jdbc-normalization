package com.workshop.loanservice.entity.modern;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;

/**
 * Modern DynamoDB entity for the LoanAccounts table.
 * Replaces the legacy CDW_LN_ACCT table. Normalized: no duplicated borrower fields.
 *
 * <p>Table: LoanAccounts
 * <ul>
 *   <li>Partition Key: account_number (S)</li>
 *   <li>GSI BorrowerIndex: borrower_id (PK) + origination_date (SK)</li>
 *   <li>GSI StatusIndex: status (PK) + account_number (SK)</li>
 *   <li>GSI ProductIndex: product_code (PK) + account_number (SK)</li>
 * </ul>
 */
@DynamoDbBean
public class LoanAccount {

    private String accountNumber;
    private String borrowerId;
    private String productCode;
    private BigDecimal originalAmount;
    private BigDecimal currentBalance;
    private BigDecimal interestRate;
    private Integer termMonths;
    private BigDecimal monthlyPayment;
    private String originationDate;
    private String maturityDate;
    private String firstPaymentDate;
    private String nextPaymentDate;
    private String status;
    private Integer delinquencyDays;
    private BigDecimal escrowBalance;
    private BigDecimal ltvPercent;
    private String propertyAddress;
    private String propertyCity;
    private String propertyState;
    private String propertyZip;
    private String propertyType;
    private BigDecimal appraisedValue;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("account_number")
    @DynamoDbSecondarySortKey(indexNames = {"StatusIndex", "ProductIndex"})
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    @DynamoDbAttribute("borrower_id")
    @DynamoDbSecondaryPartitionKey(indexNames = "BorrowerIndex")
    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String borrowerId) { this.borrowerId = borrowerId; }

    @DynamoDbAttribute("product_code")
    @DynamoDbSecondaryPartitionKey(indexNames = "ProductIndex")
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    @DynamoDbAttribute("original_amount")
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    @DynamoDbAttribute("current_balance")
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    @DynamoDbAttribute("interest_rate")
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    @DynamoDbAttribute("term_months")
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }

    @DynamoDbAttribute("monthly_payment")
    public BigDecimal getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(BigDecimal monthlyPayment) { this.monthlyPayment = monthlyPayment; }

    @DynamoDbAttribute("origination_date")
    @DynamoDbSecondarySortKey(indexNames = "BorrowerIndex")
    public String getOriginationDate() { return originationDate; }
    public void setOriginationDate(String originationDate) { this.originationDate = originationDate; }

    @DynamoDbAttribute("maturity_date")
    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }

    @DynamoDbAttribute("first_payment_date")
    public String getFirstPaymentDate() { return firstPaymentDate; }
    public void setFirstPaymentDate(String firstPaymentDate) { this.firstPaymentDate = firstPaymentDate; }

    @DynamoDbAttribute("next_payment_date")
    public String getNextPaymentDate() { return nextPaymentDate; }
    public void setNextPaymentDate(String nextPaymentDate) { this.nextPaymentDate = nextPaymentDate; }

    @DynamoDbAttribute("status")
    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbAttribute("delinquency_days")
    public Integer getDelinquencyDays() { return delinquencyDays; }
    public void setDelinquencyDays(Integer delinquencyDays) { this.delinquencyDays = delinquencyDays; }

    @DynamoDbAttribute("escrow_balance")
    public BigDecimal getEscrowBalance() { return escrowBalance; }
    public void setEscrowBalance(BigDecimal escrowBalance) { this.escrowBalance = escrowBalance; }

    @DynamoDbAttribute("ltv_percent")
    public BigDecimal getLtvPercent() { return ltvPercent; }
    public void setLtvPercent(BigDecimal ltvPercent) { this.ltvPercent = ltvPercent; }

    @DynamoDbAttribute("property_address")
    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

    @DynamoDbAttribute("property_city")
    public String getPropertyCity() { return propertyCity; }
    public void setPropertyCity(String propertyCity) { this.propertyCity = propertyCity; }

    @DynamoDbAttribute("property_state")
    public String getPropertyState() { return propertyState; }
    public void setPropertyState(String propertyState) { this.propertyState = propertyState; }

    @DynamoDbAttribute("property_zip")
    public String getPropertyZip() { return propertyZip; }
    public void setPropertyZip(String propertyZip) { this.propertyZip = propertyZip; }

    @DynamoDbAttribute("property_type")
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }

    @DynamoDbAttribute("appraised_value")
    public BigDecimal getAppraisedValue() { return appraisedValue; }
    public void setAppraisedValue(BigDecimal appraisedValue) { this.appraisedValue = appraisedValue; }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @DynamoDbAttribute("updated_at")
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

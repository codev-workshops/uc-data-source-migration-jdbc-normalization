package com.workshop.loanservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the legacy CDW_LN_ACCT table.
 * Note the denormalized borrower fields embedded in the loan record.
 *
 * @deprecated The modern normalized schema is the application's source of truth.
 * Retained only as input for {@link com.workshop.loanservice.migration.DataMigrationService}.
 */
@Deprecated
@Entity
@Table(name = "CDW_LN_ACCT")
public class LegacyLoanAccount {

    @Id
    @Column(name = "LN_ACCT_NBR")
    private String loanAccountNumber;

    @Column(name = "BORR_ID")
    private String borrowerId;

    @Column(name = "BORR_FST_NM")
    private String borrowerFirstName;

    @Column(name = "BORR_LST_NM")
    private String borrowerLastName;

    @Column(name = "BORR_SSN_LST4")
    private String borrowerSsnLast4;

    @Column(name = "PROD_CD")
    private String productCode;

    @Column(name = "LN_ORIG_AMT")
    private String originalAmount;

    @Column(name = "LN_CURR_BAL")
    private String currentBalance;

    @Column(name = "LN_INT_RT")
    private String interestRate;

    @Column(name = "LN_TERM_MOS")
    private String termMonths;

    @Column(name = "LN_PMT_AMT")
    private String monthlyPayment;

    @Column(name = "LN_ORIG_DT")
    private String originationDate;

    @Column(name = "LN_MAT_DT")
    private String maturityDate;

    @Column(name = "LN_1ST_PMT_DT")
    private String firstPaymentDate;

    @Column(name = "LN_NXT_PMT_DT")
    private String nextPaymentDate;

    @Column(name = "LN_STAT_CD")
    private String statusCode;

    @Column(name = "LN_DLQ_DAYS")
    private String delinquencyDays;

    @Column(name = "LN_ESCROW_BAL")
    private String escrowBalance;

    @Column(name = "LN_LTV_PCT")
    private String ltvPercent;

    @Column(name = "PROP_ADDR_LN1")
    private String propertyAddress;

    @Column(name = "PROP_CTY_NM")
    private String propertyCity;

    @Column(name = "PROP_ST_CD")
    private String propertyState;

    @Column(name = "PROP_ZIP_CD")
    private String propertyZip;

    @Column(name = "PROP_TYP_CD")
    private String propertyType;

    @Column(name = "PROP_APRS_VAL")
    private String appraisedValue;

    @Column(name = "LN_CRET_DT")
    private String createdDate;

    @Column(name = "LN_UPDT_DT")
    private String updatedDate;

    public String getLoanAccountNumber() { return loanAccountNumber; }
    public void setLoanAccountNumber(String loanAccountNumber) { this.loanAccountNumber = loanAccountNumber; }
    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String borrowerId) { this.borrowerId = borrowerId; }
    public String getBorrowerFirstName() { return borrowerFirstName; }
    public void setBorrowerFirstName(String borrowerFirstName) { this.borrowerFirstName = borrowerFirstName; }
    public String getBorrowerLastName() { return borrowerLastName; }
    public void setBorrowerLastName(String borrowerLastName) { this.borrowerLastName = borrowerLastName; }
    public String getBorrowerSsnLast4() { return borrowerSsnLast4; }
    public void setBorrowerSsnLast4(String borrowerSsnLast4) { this.borrowerSsnLast4 = borrowerSsnLast4; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(String originalAmount) { this.originalAmount = originalAmount; }
    public String getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(String currentBalance) { this.currentBalance = currentBalance; }
    public String getInterestRate() { return interestRate; }
    public void setInterestRate(String interestRate) { this.interestRate = interestRate; }
    public String getTermMonths() { return termMonths; }
    public void setTermMonths(String termMonths) { this.termMonths = termMonths; }
    public String getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(String monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    public String getOriginationDate() { return originationDate; }
    public void setOriginationDate(String originationDate) { this.originationDate = originationDate; }
    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }
    public String getFirstPaymentDate() { return firstPaymentDate; }
    public void setFirstPaymentDate(String firstPaymentDate) { this.firstPaymentDate = firstPaymentDate; }
    public String getNextPaymentDate() { return nextPaymentDate; }
    public void setNextPaymentDate(String nextPaymentDate) { this.nextPaymentDate = nextPaymentDate; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getDelinquencyDays() { return delinquencyDays; }
    public void setDelinquencyDays(String delinquencyDays) { this.delinquencyDays = delinquencyDays; }
    public String getEscrowBalance() { return escrowBalance; }
    public void setEscrowBalance(String escrowBalance) { this.escrowBalance = escrowBalance; }
    public String getLtvPercent() { return ltvPercent; }
    public void setLtvPercent(String ltvPercent) { this.ltvPercent = ltvPercent; }
    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }
    public String getPropertyCity() { return propertyCity; }
    public void setPropertyCity(String propertyCity) { this.propertyCity = propertyCity; }
    public String getPropertyState() { return propertyState; }
    public void setPropertyState(String propertyState) { this.propertyState = propertyState; }
    public String getPropertyZip() { return propertyZip; }
    public void setPropertyZip(String propertyZip) { this.propertyZip = propertyZip; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getAppraisedValue() { return appraisedValue; }
    public void setAppraisedValue(String appraisedValue) { this.appraisedValue = appraisedValue; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}

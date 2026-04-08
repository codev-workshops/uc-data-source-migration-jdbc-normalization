package com.workshop.loanservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the legacy CDW_PMT_HIST table.
 *
 * @deprecated Superseded by {@link Payment}. Retained for the
 *             {@link com.workshop.loanservice.service.DataMigrationService}
 *             which reads legacy data on startup.
 */
@Deprecated
@Entity
@Table(name = "CDW_PMT_HIST")
public class LegacyPayment {

    @Id
    @Column(name = "PMT_SEQ_NBR")
    private String paymentSequenceNumber;

    @Column(name = "LN_ACCT_NBR")
    private String loanAccountNumber;

    @Column(name = "PMT_DT")
    private String paymentDate;

    @Column(name = "PMT_AMT")
    private String totalAmount;

    @Column(name = "PMT_PRIN_AMT")
    private String principalAmount;

    @Column(name = "PMT_INT_AMT")
    private String interestAmount;

    @Column(name = "PMT_ESCROW_AMT")
    private String escrowAmount;

    @Column(name = "PMT_LATE_FEE")
    private String lateFee;

    @Column(name = "PMT_TYP_CD")
    private String typeCode;

    @Column(name = "PMT_STAT_CD")
    private String statusCode;

    @Column(name = "PMT_RECV_DT")
    private String receivedDate;

    @Column(name = "PMT_PROC_DT")
    private String processedDate;

    @Column(name = "PMT_CRET_DT")
    private String createdDate;

    @Column(name = "PMT_UPDT_DT")
    private String updatedDate;

    public String getPaymentSequenceNumber() { return paymentSequenceNumber; }
    public void setPaymentSequenceNumber(String paymentSequenceNumber) { this.paymentSequenceNumber = paymentSequenceNumber; }
    public String getLoanAccountNumber() { return loanAccountNumber; }
    public void setLoanAccountNumber(String loanAccountNumber) { this.loanAccountNumber = loanAccountNumber; }
    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }
    public String getTotalAmount() { return totalAmount; }
    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }
    public String getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(String principalAmount) { this.principalAmount = principalAmount; }
    public String getInterestAmount() { return interestAmount; }
    public void setInterestAmount(String interestAmount) { this.interestAmount = interestAmount; }
    public String getEscrowAmount() { return escrowAmount; }
    public void setEscrowAmount(String escrowAmount) { this.escrowAmount = escrowAmount; }
    public String getLateFee() { return lateFee; }
    public void setLateFee(String lateFee) { this.lateFee = lateFee; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getReceivedDate() { return receivedDate; }
    public void setReceivedDate(String receivedDate) { this.receivedDate = receivedDate; }
    public String getProcessedDate() { return processedDate; }
    public void setProcessedDate(String processedDate) { this.processedDate = processedDate; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}

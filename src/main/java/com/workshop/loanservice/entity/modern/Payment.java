package com.workshop.loanservice.entity.modern;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;

/**
 * Modern DynamoDB entity for the Payments table.
 * Replaces the legacy CDW_PMT_HIST table.
 *
 * <p>Table: Payments
 * <ul>
 *   <li>Partition Key: loan_account_id (S)</li>
 *   <li>Sort Key: payment_sort_key (S) - composite "{YYYY-MM-DD}#{payment_id}"</li>
 *   <li>GSI PaymentIdIndex: payment_id (PK) - direct lookup by payment ID</li>
 *   <li>GSI StatusIndex: status (PK) + payment_date (SK)</li>
 * </ul>
 *
 * <p>The composite sort key enables chronological ordering within a loan partition.
 * Query with ScanIndexForward=false for descending (newest-first) order.
 */
@DynamoDbBean
public class Payment {

    private String loanAccountId;
    private String paymentSortKey;
    private String paymentId;
    private String paymentDate;
    private BigDecimal totalAmount;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal escrowAmount;
    private BigDecimal lateFee;
    private String type;
    private String status;
    private String receivedDate;
    private String processedDate;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("loan_account_id")
    public String getLoanAccountId() { return loanAccountId; }
    public void setLoanAccountId(String loanAccountId) { this.loanAccountId = loanAccountId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("payment_sort_key")
    public String getPaymentSortKey() { return paymentSortKey; }
    public void setPaymentSortKey(String paymentSortKey) { this.paymentSortKey = paymentSortKey; }

    @DynamoDbAttribute("payment_id")
    @DynamoDbSecondaryPartitionKey(indexNames = "PaymentIdIndex")
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    @DynamoDbAttribute("payment_date")
    @DynamoDbSecondarySortKey(indexNames = "StatusIndex")
    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }

    @DynamoDbAttribute("total_amount")
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    @DynamoDbAttribute("principal_amount")
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }

    @DynamoDbAttribute("interest_amount")
    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }

    @DynamoDbAttribute("escrow_amount")
    public BigDecimal getEscrowAmount() { return escrowAmount; }
    public void setEscrowAmount(BigDecimal escrowAmount) { this.escrowAmount = escrowAmount; }

    @DynamoDbAttribute("late_fee")
    public BigDecimal getLateFee() { return lateFee; }
    public void setLateFee(BigDecimal lateFee) { this.lateFee = lateFee; }

    @DynamoDbAttribute("type")
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @DynamoDbAttribute("status")
    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbAttribute("received_date")
    public String getReceivedDate() { return receivedDate; }
    public void setReceivedDate(String receivedDate) { this.receivedDate = receivedDate; }

    @DynamoDbAttribute("processed_date")
    public String getProcessedDate() { return processedDate; }
    public void setProcessedDate(String processedDate) { this.processedDate = processedDate; }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @DynamoDbAttribute("updated_at")
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

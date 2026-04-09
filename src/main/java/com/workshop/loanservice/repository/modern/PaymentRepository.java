package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Payment;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB repository for the Payments table.
 * Provides access patterns: get payments by loan (date-ordered), get payment by ID.
 *
 * <p>The table uses a composite sort key ({@code {date}#{payment_id}}) so querying
 * by partition key returns payments in chronological order. Use
 * {@code ScanIndexForward=false} for reverse-chronological (newest-first).
 */
@Repository
public class PaymentRepository {

    private final DynamoDbTable<Payment> table;
    private final DynamoDbIndex<Payment> paymentIdIndex;
    private final DynamoDbIndex<Payment> statusIndex;

    public PaymentRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("Payments", TableSchema.fromBean(Payment.class));
        this.paymentIdIndex = table.index("PaymentIdIndex");
        this.statusIndex = table.index("StatusIndex");
    }

    /**
     * Get all payments for a loan account, ordered by date descending (newest first).
     */
    public List<Payment> findByLoanAccountIdOrderByDateDesc(String loanAccountId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(loanAccountId).build());
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .scanIndexForward(false)
                .build();
        return table.query(request).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Get all payments for a loan account, ordered by date ascending.
     */
    public List<Payment> findByLoanAccountId(String loanAccountId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(loanAccountId).build());
        return table.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Get a specific payment by its unique payment ID (via GSI).
     */
    public Optional<Payment> findByPaymentId(String paymentId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(paymentId).build());
        return paymentIdIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /**
     * Get payments by status, ordered by payment date.
     */
    public List<Payment> findByStatus(String status) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status).build());
        return statusIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public void save(Payment payment) {
        table.putItem(payment);
    }

    public void delete(String loanAccountId, String paymentSortKey) {
        Key key = Key.builder()
                .partitionValue(loanAccountId)
                .sortValue(paymentSortKey)
                .build();
        table.deleteItem(key);
    }
}

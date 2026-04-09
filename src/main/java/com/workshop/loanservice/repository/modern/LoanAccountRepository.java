package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanAccount;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB repository for the LoanAccounts table.
 * Provides access patterns: get by account number, find by borrower, find by status, find by product.
 */
@Repository
public class LoanAccountRepository {

    private final DynamoDbTable<LoanAccount> table;
    private final DynamoDbIndex<LoanAccount> borrowerIndex;
    private final DynamoDbIndex<LoanAccount> statusIndex;
    private final DynamoDbIndex<LoanAccount> productIndex;

    public LoanAccountRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("LoanAccounts", TableSchema.fromBean(LoanAccount.class));
        this.borrowerIndex = table.index("BorrowerIndex");
        this.statusIndex = table.index("StatusIndex");
        this.productIndex = table.index("ProductIndex");
    }

    public Optional<LoanAccount> findById(String accountNumber) {
        Key key = Key.builder().partitionValue(accountNumber).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<LoanAccount> findAll() {
        return table.scan().items().stream().collect(Collectors.toList());
    }

    public List<LoanAccount> findByBorrowerId(String borrowerId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(borrowerId).build());
        return borrowerIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<LoanAccount> findByStatus(String status) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status).build());
        return statusIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<LoanAccount> findByProductCode(String productCode) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(productCode).build());
        return productIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public void save(LoanAccount account) {
        table.putItem(account);
    }

    public void delete(String accountNumber) {
        Key key = Key.builder().partitionValue(accountNumber).build();
        table.deleteItem(key);
    }
}

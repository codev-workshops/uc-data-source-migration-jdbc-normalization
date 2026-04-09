package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Borrower;
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
 * DynamoDB repository for the Borrowers table.
 * Provides access patterns: get by ID, list all, find by status, find by last name.
 */
@Repository
public class BorrowerRepository {

    private final DynamoDbTable<Borrower> table;
    private final DynamoDbIndex<Borrower> statusIndex;
    private final DynamoDbIndex<Borrower> lastNameIndex;

    public BorrowerRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("Borrowers", TableSchema.fromBean(Borrower.class));
        this.statusIndex = table.index("StatusIndex");
        this.lastNameIndex = table.index("LastNameIndex");
    }

    public Optional<Borrower> findById(String borrowerId) {
        Key key = Key.builder().partitionValue(borrowerId).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<Borrower> findAll() {
        return table.scan().items().stream().collect(Collectors.toList());
    }

    public List<Borrower> findByStatus(String status) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status).build());
        return statusIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Borrower> findByLastName(String lastName) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(lastName).build());
        return lastNameIndex.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public void save(Borrower borrower) {
        table.putItem(borrower);
    }

    public void delete(String borrowerId) {
        Key key = Key.builder().partitionValue(borrowerId).build();
        table.deleteItem(key);
    }
}

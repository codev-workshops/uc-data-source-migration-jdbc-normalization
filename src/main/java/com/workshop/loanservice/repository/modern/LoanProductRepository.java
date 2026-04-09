package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanProduct;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB repository for the LoanProducts table.
 * Small reference table; scan is acceptable for findAll().
 */
@Repository
public class LoanProductRepository {

    private final DynamoDbTable<LoanProduct> table;

    public LoanProductRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("LoanProducts", TableSchema.fromBean(LoanProduct.class));
    }

    public Optional<LoanProduct> findById(String productCode) {
        Key key = Key.builder().partitionValue(productCode).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<LoanProduct> findAll() {
        return table.scan().items().stream().collect(Collectors.toList());
    }

    public void save(LoanProduct product) {
        table.putItem(product);
    }

    public void delete(String productCode) {
        Key key = Key.builder().partitionValue(productCode).build();
        table.deleteItem(key);
    }
}

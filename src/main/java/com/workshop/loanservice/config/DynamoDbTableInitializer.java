package com.workshop.loanservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

/**
 * Creates DynamoDB tables on startup when the "dynamodb-init" profile is active.
 * Use this with DynamoDB Local for development:
 *
 * <pre>
 *   java -jar app.jar --spring.profiles.active=dynamodb-init \
 *                      --dynamodb.endpoint=http://localhost:8000
 * </pre>
 */
@Configuration
@Profile("dynamodb-init")
public class DynamoDbTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

    @Bean
    public CommandLineRunner initDynamoDbTables(DynamoDbClient dynamoDbClient) {
        return args -> {
            createBorrowersTable(dynamoDbClient);
            createLoanProductsTable(dynamoDbClient);
            createLoanAccountsTable(dynamoDbClient);
            createPaymentsTable(dynamoDbClient);
            log.info("DynamoDB table initialization complete.");
        };
    }

    private void createBorrowersTable(DynamoDbClient client) {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName("Borrowers")
                    .keySchema(key("borrower_id", KeyType.HASH))
                    .attributeDefinitions(
                            attr("borrower_id", ScalarAttributeType.S),
                            attr("status", ScalarAttributeType.S),
                            attr("email", ScalarAttributeType.S),
                            attr("last_name", ScalarAttributeType.S))
                    .globalSecondaryIndexes(
                            gsi("StatusIndex", "status", "borrower_id"),
                            gsi("EmailIndex", "email", null),
                            gsi("LastNameIndex", "last_name", "borrower_id"))
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            log.info("Created table: Borrowers");
        } catch (ResourceInUseException e) {
            log.info("Table already exists: Borrowers");
        }
    }

    private void createLoanProductsTable(DynamoDbClient client) {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName("LoanProducts")
                    .keySchema(key("product_code", KeyType.HASH))
                    .attributeDefinitions(attr("product_code", ScalarAttributeType.S))
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            log.info("Created table: LoanProducts");
        } catch (ResourceInUseException e) {
            log.info("Table already exists: LoanProducts");
        }
    }

    private void createLoanAccountsTable(DynamoDbClient client) {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName("LoanAccounts")
                    .keySchema(key("account_number", KeyType.HASH))
                    .attributeDefinitions(
                            attr("account_number", ScalarAttributeType.S),
                            attr("borrower_id", ScalarAttributeType.S),
                            attr("origination_date", ScalarAttributeType.S),
                            attr("status", ScalarAttributeType.S),
                            attr("product_code", ScalarAttributeType.S))
                    .globalSecondaryIndexes(
                            gsi("BorrowerIndex", "borrower_id", "origination_date"),
                            gsi("StatusIndex", "status", "account_number"),
                            gsi("ProductIndex", "product_code", "account_number"))
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            log.info("Created table: LoanAccounts");
        } catch (ResourceInUseException e) {
            log.info("Table already exists: LoanAccounts");
        }
    }

    private void createPaymentsTable(DynamoDbClient client) {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName("Payments")
                    .keySchema(
                            key("loan_account_id", KeyType.HASH),
                            key("payment_sort_key", KeyType.RANGE))
                    .attributeDefinitions(
                            attr("loan_account_id", ScalarAttributeType.S),
                            attr("payment_sort_key", ScalarAttributeType.S),
                            attr("payment_id", ScalarAttributeType.S),
                            attr("payment_date", ScalarAttributeType.S),
                            attr("status", ScalarAttributeType.S))
                    .globalSecondaryIndexes(
                            gsi("PaymentIdIndex", "payment_id", null),
                            gsi("StatusIndex", "status", "payment_date"))
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            log.info("Created table: Payments");
        } catch (ResourceInUseException e) {
            log.info("Table already exists: Payments");
        }
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static AttributeDefinition attr(String name, ScalarAttributeType type) {
        return AttributeDefinition.builder().attributeName(name).attributeType(type).build();
    }

    private static GlobalSecondaryIndex gsi(String indexName, String hashKey, String rangeKey) {
        GlobalSecondaryIndex.Builder builder = GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build());

        if (rangeKey != null) {
            builder.keySchema(
                    key(hashKey, KeyType.HASH),
                    key(rangeKey, KeyType.RANGE));
        } else {
            builder.keySchema(key(hashKey, KeyType.HASH));
        }
        return builder.build();
    }

}

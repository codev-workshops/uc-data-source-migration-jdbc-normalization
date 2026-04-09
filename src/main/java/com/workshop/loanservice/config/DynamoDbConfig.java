package com.workshop.loanservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * DynamoDB client configuration.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Local development:</b> Set {@code dynamodb.endpoint} to a local DynamoDB endpoint
 *       (e.g., {@code http://localhost:8000} for DynamoDB Local)</li>
 *   <li><b>AWS:</b> Leave {@code dynamodb.endpoint} empty to use standard AWS credentials
 *       and the configured region</li>
 * </ul>
 */
@Configuration
public class DynamoDbConfig {

    @Value("${dynamodb.endpoint:}")
    private String endpoint;

    @Value("${dynamodb.region:us-east-1}")
    private String region;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        if (endpoint != null && !endpoint.isBlank()) {
            return DynamoDbClient.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")))
                    .build();
        }
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}

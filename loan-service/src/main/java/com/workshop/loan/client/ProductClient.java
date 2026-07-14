package com.workshop.loan.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * REST client to product-service. Replaces the in-process product lookup the
 * monolith did by joining the loan_products table.
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private final RestClient restClient;

    public ProductClient(RestClient.Builder builder,
                         @Value("${services.product.url}") String productServiceUrl) {
        this.restClient = builder.baseUrl(productServiceUrl).build();
    }

    public Optional<ProductRef> findByCode(String code) {
        try {
            ProductRef ref = restClient.get()
                    .uri("/api/products/{code}", code)
                    .retrieve()
                    .body(ProductRef.class);
            return Optional.ofNullable(ref);
        } catch (RestClientException ex) {
            log.warn("Unable to fetch product {} from product-service: {}", code, ex.getMessage());
            return Optional.empty();
        }
    }
}

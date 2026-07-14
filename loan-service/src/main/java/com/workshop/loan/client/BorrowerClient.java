package com.workshop.loan.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * REST client to borrower-service. Replaces the denormalized borrower name/SSN
 * columns that the legacy loan table carried.
 */
@Component
public class BorrowerClient {

    private static final Logger log = LoggerFactory.getLogger(BorrowerClient.class);

    private final RestClient restClient;

    public BorrowerClient(RestClient.Builder builder,
                          @Value("${services.borrower.url}") String borrowerServiceUrl) {
        this.restClient = builder.baseUrl(borrowerServiceUrl).build();
    }

    public Optional<BorrowerRef> findByExternalId(String externalId) {
        try {
            BorrowerRef ref = restClient.get()
                    .uri("/api/borrowers/{id}", externalId)
                    .retrieve()
                    .body(BorrowerRef.class);
            return Optional.ofNullable(ref);
        } catch (RestClientException ex) {
            log.warn("Unable to fetch borrower {} from borrower-service: {}", externalId, ex.getMessage());
            return Optional.empty();
        }
    }
}

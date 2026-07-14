package com.workshop.borrower.client;

import com.workshop.borrower.dto.BorrowerLoanDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * REST client to the loan bounded context. Replaces the monolith's in-process
 * join between borrowers and loan accounts.
 */
@Component
public class LoanClient {

    private static final Logger log = LoggerFactory.getLogger(LoanClient.class);

    private final RestClient restClient;

    public LoanClient(RestClient.Builder builder,
                      @Value("${services.loan.url}") String loanServiceUrl) {
        this.restClient = builder.baseUrl(loanServiceUrl).build();
    }

    public List<BorrowerLoanDto> getLoansForBorrower(String borrowerExternalId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/loans")
                            .queryParam("borrowerId", borrowerExternalId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<BorrowerLoanDto>>() {});
        } catch (RestClientException ex) {
            log.warn("Unable to fetch loans for borrower {} from loan-service: {}",
                    borrowerExternalId, ex.getMessage());
            return List.of();
        }
    }
}

package com.workshop.borrowerservice.client;

import com.workshop.common.dto.LoanSummaryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class LoanServiceClient {

    private final RestTemplate restTemplate;
    private final String loanServiceUrl;

    public LoanServiceClient(RestTemplate restTemplate,
                             @Value("${services.loan-service.url}") String loanServiceUrl) {
        this.restTemplate = restTemplate;
        this.loanServiceUrl = loanServiceUrl;
    }

    public List<LoanSummaryDto> getLoansByBorrowerId(String borrowerId) {
        try {
            ResponseEntity<List<LoanSummaryDto>> response = restTemplate.exchange(
                    loanServiceUrl + "/internal/loans/by-borrower/" + borrowerId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (RestClientException e) {
            return Collections.emptyList();
        }
    }
}

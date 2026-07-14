package com.workshop.loan.client;

import com.workshop.loan.dto.PaymentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * REST client to payment-service. Payment history is owned by the payment
 * bounded context and referenced here by loan account number via API — not a
 * shared table.
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient restClient;

    public PaymentClient(RestClient.Builder builder,
                         @Value("${services.payment.url}") String paymentServiceUrl) {
        this.restClient = builder.baseUrl(paymentServiceUrl).build();
    }

    public List<PaymentDto> getPaymentsForLoan(String loanAccountNumber) {
        try {
            return restClient.get()
                    .uri("/api/payments/loan/{loanId}", loanAccountNumber)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PaymentDto>>() {});
        } catch (RestClientException ex) {
            log.warn("Unable to fetch payments for loan {} from payment-service: {}",
                    loanAccountNumber, ex.getMessage());
            return List.of();
        }
    }
}

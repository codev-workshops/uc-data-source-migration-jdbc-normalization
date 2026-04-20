package com.workshop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GatewayConfig {

    @Value("${services.borrower-service.url}")
    private String borrowerServiceUrl;

    @Value("${services.loan-service.url}")
    private String loanServiceUrl;

    @Value("${services.payment-service.url}")
    private String paymentServiceUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getBorrowerServiceUrl() { return borrowerServiceUrl; }
    public String getLoanServiceUrl() { return loanServiceUrl; }
    public String getPaymentServiceUrl() { return paymentServiceUrl; }
}

package com.workshop.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class GatewayController {

    private final RestTemplate restTemplate;

    @Value("${services.borrower-service.url}")
    private String borrowerServiceUrl;

    @Value("${services.loan-service.url}")
    private String loanServiceUrl;

    @Value("${services.payment-service.url}")
    private String paymentServiceUrl;

    public GatewayController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/borrowers")
    public ResponseEntity<String> getAllBorrowers() {
        return restTemplate.getForEntity(borrowerServiceUrl + "/api/borrowers", String.class);
    }

    @GetMapping("/borrowers/{id}")
    public ResponseEntity<String> getBorrower(@PathVariable String id) {
        return restTemplate.getForEntity(borrowerServiceUrl + "/api/borrowers/" + id, String.class);
    }

    @GetMapping("/loans")
    public ResponseEntity<String> getAllLoans() {
        return restTemplate.getForEntity(loanServiceUrl + "/api/loans", String.class);
    }

    @GetMapping("/loans/{id}")
    public ResponseEntity<String> getLoan(@PathVariable String id) {
        return restTemplate.getForEntity(loanServiceUrl + "/api/loans/" + id, String.class);
    }

    @GetMapping("/loans/{loanId}/payments")
    public ResponseEntity<String> getPayments(@PathVariable String loanId) {
        return restTemplate.getForEntity(loanServiceUrl + "/api/loans/" + loanId + "/payments", String.class);
    }

    @GetMapping("/payments/loan/{loanId}")
    public ResponseEntity<String> getPaymentsByLoan(@PathVariable String loanId) {
        return restTemplate.getForEntity(paymentServiceUrl + "/api/payments/loan/" + loanId, String.class);
    }
}

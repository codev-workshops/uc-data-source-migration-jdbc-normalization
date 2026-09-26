package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final LoanQueryService loanQueryService;

    public PaymentController(LoanQueryService loanQueryService) {
        this.loanQueryService = loanQueryService;
    }

    @GetMapping("/loan/{loanId}")
    public List<PaymentDto> getPaymentHistory(@PathVariable String loanId) {
        log.info("Query for payment history of loan id={}", loanId);
        return loanQueryService.getPaymentsByLoan(loanId);
    }
}

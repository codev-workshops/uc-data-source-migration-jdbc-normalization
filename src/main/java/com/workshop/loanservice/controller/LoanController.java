package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
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
@RequestMapping("/api/loans")
public class LoanController {

    private static final Logger log = LoggerFactory.getLogger(LoanController.class);

    private final LoanQueryService loanService;

    public LoanController(LoanQueryService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanSummaryDto> getAllLoans() {
        log.info("Query for all loans");
        return loanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        log.info("Query for loan id={}", id);
        return loanService.getLoanById(id);
    }

    @GetMapping("/{loanId}/payments")
    public List<PaymentDto> getPayments(@PathVariable String loanId) {
        log.info("Query for payment history of loan id={}", loanId);
        return loanService.getPaymentsByLoan(loanId);
    }
}

package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanSummaryDto> getAllLoans() {
        return loanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        return loanService.getLoanById(id);
    }

    @GetMapping("/{loanId}/payments")
    public List<PaymentDto> getPayments(@PathVariable String loanId) {
        return loanService.getPaymentsByLoan(loanId);
    }
}

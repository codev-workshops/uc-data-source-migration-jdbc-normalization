package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.ModernLoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final ModernLoanService modernLoanService;

    public LoanController(ModernLoanService modernLoanService) {
        this.modernLoanService = modernLoanService;
    }

    @GetMapping
    public List<LoanSummaryDto> getAllLoans() {
        return modernLoanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        return modernLoanService.getLoanById(id);
    }

    @GetMapping("/{loanId}/payments")
    public List<PaymentDto> getPayments(@PathVariable String loanId) {
        return modernLoanService.getPaymentsByLoan(loanId);
    }
}

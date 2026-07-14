package com.workshop.loan.controller;

import com.workshop.loan.dto.LoanSummaryDto;
import com.workshop.loan.dto.PaymentDto;
import com.workshop.loan.service.LoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<LoanSummaryDto> getAllLoans(@RequestParam(name = "borrowerId", required = false) String borrowerId) {
        if (borrowerId != null && !borrowerId.isBlank()) {
            return loanService.getLoansByBorrower(borrowerId);
        }
        return loanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        return loanService.getLoanByAccountNumber(id);
    }

    /**
     * Payment history for a loan — proxied to payment-service so the monolith's
     * {@code /api/loans/{loanId}/payments} endpoint stays reachable.
     */
    @GetMapping("/{loanId}/payments")
    public List<PaymentDto> getPayments(@PathVariable String loanId) {
        return loanService.getPaymentsByLoan(loanId);
    }
}

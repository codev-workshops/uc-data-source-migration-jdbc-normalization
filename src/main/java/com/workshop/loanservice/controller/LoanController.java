package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.service.ModernLoanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;
    private final ModernLoanService modernLoanService;
    private final boolean useModern;

    public LoanController(LoanService loanService,
                           ModernLoanService modernLoanService,
                           @Value("${app.use-modern-datasource:false}") boolean useModern) {
        this.loanService = loanService;
        this.modernLoanService = modernLoanService;
        this.useModern = useModern;
    }

    @GetMapping
    public List<LoanSummaryDto> getAllLoans() {
        return useModern ? modernLoanService.getAllLoans() : loanService.getAllLoans();
    }

    @GetMapping("/{id}")
    public LoanSummaryDto getLoan(@PathVariable String id) {
        return useModern ? modernLoanService.getLoanById(id) : loanService.getLoanById(id);
    }

    @GetMapping("/{loanId}/payments")
    public List<PaymentDto> getPayments(@PathVariable String loanId) {
        return useModern ? modernLoanService.getPaymentsByLoan(loanId) : loanService.getPaymentsByLoan(loanId);
    }
}

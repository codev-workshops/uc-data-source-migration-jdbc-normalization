package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final LoanService loanService;

    public PaymentController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/loan/{loanId}")
    public List<PaymentDto> getPaymentsByLoan(@PathVariable String loanId) {
        return loanService.getPaymentsByLoan(loanId);
    }
}

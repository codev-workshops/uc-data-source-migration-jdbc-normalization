package com.workshop.payment.controller;

import com.workshop.payment.dto.PaymentDto;
import com.workshop.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentDto> getPayments(@RequestParam("loanAccountNumber") String loanAccountNumber) {
        return paymentService.getPaymentsByLoan(loanAccountNumber);
    }

    @GetMapping("/loan/{loanId}")
    public List<PaymentDto> getPaymentsByLoan(@PathVariable String loanId) {
        return paymentService.getPaymentsByLoan(loanId);
    }
}

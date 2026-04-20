package com.workshop.paymentservice.controller;

import com.workshop.common.dto.PaymentDto;
import com.workshop.paymentservice.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/loan/{loanAccountNumber}")
    public List<PaymentDto> getPaymentsByLoanAccountNumber(@PathVariable String loanAccountNumber) {
        return paymentService.getPaymentsByLoanAccountNumber(loanAccountNumber);
    }
}

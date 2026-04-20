package com.workshop.paymentservice.controller;

import com.workshop.common.dto.PaymentDto;
import com.workshop.paymentservice.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/loan/{loanId}")
    public List<PaymentDto> getPaymentsByLoan(@PathVariable String loanId) {
        return paymentService.getPaymentsByLoanAccountNumber(loanId);
    }
}

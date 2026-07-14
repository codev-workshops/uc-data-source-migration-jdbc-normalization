package com.workshop.payment;

import com.workshop.payment.dto.PaymentDto;
import com.workshop.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentServiceApplicationTests {

    @Autowired
    private PaymentService paymentService;

    @Test
    void contextLoads() {
    }

    @Test
    void paymentsAreKeyedByLoanAccountAndTyped() {
        List<PaymentDto> payments = paymentService.getPaymentsByLoan("LN-2019-00142");
        assertThat(payments).hasSize(2);
        // Ordered by payment_date desc.
        PaymentDto latest = payments.get(0);
        assertThat(latest.getPaymentId()).isEqualTo("PMT-2025120001");
        assertThat(latest.getPaymentDate()).isEqualTo("2025-12-15");
        assertThat(latest.getTotalAmount()).isEqualByComparingTo("1487.02");
        assertThat(latest.getType()).isEqualTo("REGULAR");
        assertThat(latest.getStatus()).isEqualTo("POSTED");
    }
}

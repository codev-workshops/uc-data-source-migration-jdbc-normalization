package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:paymentmapping;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Transactional
class LoanServicePaymentMappingTest {

    @Autowired private LoanService loanService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void absentOptionalAmountsArePresentedAsZero() {
        jdbcTemplate.update(
                "INSERT INTO payments (external_id, loan_account_id, payment_date, total_amount, "
                        + "principal_amount, interest_amount, escrow_amount, late_fee, type, status) "
                        + "VALUES ('PMT-TEST000001', (SELECT id FROM loan_accounts WHERE account_number = 'LN-2019-00142'), "
                        + "DATE '2026-01-01', 100.00, NULL, NULL, NULL, NULL, 'PARTIAL', 'PENDING')");

        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");
        PaymentDto sparse = payments.get(0);

        assertEquals("PMT-TEST000001", sparse.getPaymentId());
        assertEquals(new BigDecimal("100.00"), sparse.getTotalAmount());
        assertEquals(BigDecimal.ZERO, sparse.getPrincipalAmount());
        assertEquals(BigDecimal.ZERO, sparse.getInterestAmount());
        assertEquals(BigDecimal.ZERO, sparse.getEscrowAmount());
        assertEquals(BigDecimal.ZERO, sparse.getLateFee());
        assertEquals("Partial", sparse.getType());
        assertEquals("Pending", sparse.getStatus());
    }
}

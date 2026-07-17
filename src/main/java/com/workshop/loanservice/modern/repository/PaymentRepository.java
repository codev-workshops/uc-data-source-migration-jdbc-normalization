package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for modern {@link Payment} entities. The single derived
 * query preserves the legacy API's payment ordering (most recent first).
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Payments for a loan, newest first — reproduces the legacy {@code PMT_DT DESC}
     * ordering the API contract pins. Matched via the loan's legacy account number.
     */
    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(String accountNumber);
}

package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);

    Optional<Payment> findByLegacyPaymentId(String legacyPaymentId);
}

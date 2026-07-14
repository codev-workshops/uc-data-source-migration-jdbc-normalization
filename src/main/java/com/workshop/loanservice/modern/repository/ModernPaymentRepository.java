package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModernPaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByLoanAccountId(Long loanAccountId);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);

    boolean existsByPaymentNumber(String paymentNumber);
}

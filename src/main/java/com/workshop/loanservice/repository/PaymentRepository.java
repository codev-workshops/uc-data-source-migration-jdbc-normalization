package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the modern {@link Payment} entity.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(String accountNumber);

    long countByLoanAccount_AccountNumber(String accountNumber);
}

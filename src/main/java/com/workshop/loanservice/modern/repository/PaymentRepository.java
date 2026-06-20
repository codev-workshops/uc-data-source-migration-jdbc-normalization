package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByExternalId(String externalId);

    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(String accountNumber);
}

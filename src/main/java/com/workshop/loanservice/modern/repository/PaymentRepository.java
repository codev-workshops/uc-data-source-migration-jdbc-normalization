package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(String accountNumber);
}

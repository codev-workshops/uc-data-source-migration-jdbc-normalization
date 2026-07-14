package com.workshop.payment.repository;

import com.workshop.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

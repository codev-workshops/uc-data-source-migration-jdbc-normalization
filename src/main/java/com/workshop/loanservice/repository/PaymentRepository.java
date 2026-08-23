package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccountAccountNumber(String accountNumber);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);
}

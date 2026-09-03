package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"loanAccount"})
    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(String accountNumber);
}

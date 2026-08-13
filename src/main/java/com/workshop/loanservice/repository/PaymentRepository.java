package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccountOrderByPaymentDateDesc(LoanAccount loanAccount);

    List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);
}

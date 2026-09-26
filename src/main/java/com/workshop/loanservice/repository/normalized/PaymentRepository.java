package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

  List<Payment> findByLoanAccountLoanAccountNumber(String loanAccountNumber);

  List<Payment> findByLoanAccountLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

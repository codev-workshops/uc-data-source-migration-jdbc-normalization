package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByExternalId(String externalId);

  List<Payment> findByLoanAccountAccountNumber(String accountNumber);

  List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);

  List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);
}

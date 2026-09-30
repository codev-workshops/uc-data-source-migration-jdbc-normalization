package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);

  Optional<Payment> findByLegacyPaymentId(String legacyPaymentId);
}

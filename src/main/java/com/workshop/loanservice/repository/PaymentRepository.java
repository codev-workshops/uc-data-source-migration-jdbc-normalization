package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link Payment}. */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByLegacyPaymentId(String legacyPaymentId);

  List<Payment> findByLoanAccountAccountNumber(String accountNumber);

  @EntityGraph(attributePaths = {"loanAccount"})
  List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);
}

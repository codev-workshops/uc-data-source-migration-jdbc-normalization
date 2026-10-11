package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @EntityGraph(attributePaths = {"loanAccount"})
  List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);

  @EntityGraph(attributePaths = {"loanAccount"})
  Optional<Payment> findByExternalId(String externalId);

  boolean existsByExternalId(String externalId);
}

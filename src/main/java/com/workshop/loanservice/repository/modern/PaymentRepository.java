package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "loanAccount")
    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDescIdAsc(String accountNumber);

    @EntityGraph(attributePaths = "loanAccount")
    Optional<Payment> findByExternalId(String externalId);
}

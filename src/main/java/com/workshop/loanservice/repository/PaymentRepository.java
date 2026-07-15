package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "loanAccount")
    Optional<Payment> findByExternalId(String externalId);

    @EntityGraph(attributePaths = "loanAccount")
    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDescIdDesc(
            String accountNumber
    );

    @EntityGraph(attributePaths = "loanAccount")
    List<Payment> findAllByOrderByIdAsc();

    boolean existsByExternalId(String externalId);
}

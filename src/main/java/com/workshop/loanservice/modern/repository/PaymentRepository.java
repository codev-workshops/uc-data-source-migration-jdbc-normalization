package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByExternalId(String externalId);

    // loanAccount is LAZY and the DTO needs its account number, so it is
    // fetch-joined here to avoid a per-payment lookup (no N+1).
    @Query("select p from Payment p join fetch p.loanAccount la "
            + "where la.accountNumber = :accountNumber order by p.paymentDate desc")
    List<Payment> findByLoanAccount_AccountNumberOrderByPaymentDateDesc(
            @Param("accountNumber") String accountNumber);
}

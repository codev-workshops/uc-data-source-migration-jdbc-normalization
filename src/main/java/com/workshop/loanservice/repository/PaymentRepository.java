package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p JOIN FETCH p.loanAccount la WHERE la.accountNumber = :accountNumber ORDER BY p.paymentDate DESC")
    List<Payment> findByLoanAccountNumberOrderByPaymentDateDesc(@Param("accountNumber") String accountNumber);
}

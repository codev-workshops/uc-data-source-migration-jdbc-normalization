package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByExternalId(String externalId);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDescExternalIdDesc(String accountNumber);
}

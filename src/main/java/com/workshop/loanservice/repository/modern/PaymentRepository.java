package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByLoanAccountId(Long loanAccountId);

    List<Payment> findByLoanAccountIdOrderByPaymentDateDesc(Long loanAccountId);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDesc(String accountNumber);

    List<Payment> findByLoanAccountAccountNumberOrderByPaymentDateDescIdAsc(String accountNumber);
}

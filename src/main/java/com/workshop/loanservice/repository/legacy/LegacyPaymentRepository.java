package com.workshop.loanservice.repository.legacy;

import com.workshop.loanservice.entity.legacy.LegacyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Superseded by {@link PaymentRepository}. Retained for the
 *             {@link com.workshop.loanservice.service.DataMigrationService}.
 */
@Deprecated
@Repository
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

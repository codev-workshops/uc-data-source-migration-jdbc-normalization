package com.workshop.loanservice.legacy.repository;

import com.workshop.loanservice.legacy.entity.LegacyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Legacy CDW repository. Used only by the data migration and the
 * {@code LEGACY} dual-read fallback; prefer the modern repositories.
 */
@Deprecated
@Repository
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

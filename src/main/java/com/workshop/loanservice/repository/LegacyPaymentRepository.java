package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository over the legacy CDW_PMT_HIST table.
 *
 * @deprecated backs only the legacy/fallback read path ({@code loanservice.datasource.mode=legacy},
 *     and the dual-read fallback when the modern schema returns nothing) plus the migration's source
 *     reads. The modern replacement is
 *     {@link com.workshop.loanservice.modern.repository.PaymentRepository}.
 */
@Deprecated
@Repository
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

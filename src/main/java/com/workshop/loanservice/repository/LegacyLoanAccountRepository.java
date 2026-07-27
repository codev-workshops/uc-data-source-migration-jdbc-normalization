package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository over the legacy CDW_LN_ACCT table.
 *
 * @deprecated backs only the legacy/fallback read path ({@code loanservice.datasource.mode=legacy},
 *     and the dual-read fallback when the modern schema returns nothing) plus the migration's source
 *     reads. The modern replacement is
 *     {@link com.workshop.loanservice.modern.repository.LoanAccountRepository}.
 */
@Deprecated
@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);
}

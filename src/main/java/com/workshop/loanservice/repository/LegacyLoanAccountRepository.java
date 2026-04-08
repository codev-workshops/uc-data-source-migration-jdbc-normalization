package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Replaced by {@link ModernLoanAccountRepository}. Scheduled for removal in Phase 6.
 */
@Deprecated
@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);
}

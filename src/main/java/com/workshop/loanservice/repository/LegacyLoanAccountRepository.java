package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated the modern schema is the operative data source; use
 * {@link com.workshop.loanservice.modern.repository.ModernLoanAccountRepository}
 * instead. Retained only for the dual-read fallback
 * ({@code datasource.mode=legacy}) and as the migration source.
 */
@Deprecated
@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);
}

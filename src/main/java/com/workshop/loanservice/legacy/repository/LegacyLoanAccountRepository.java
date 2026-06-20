package com.workshop.loanservice.legacy.repository;

import com.workshop.loanservice.legacy.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Legacy CDW repository. Used only by the data migration and the
 * {@code LEGACY} dual-read fallback; prefer the modern repositories.
 */
@Deprecated
@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);
}

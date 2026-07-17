package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for modern {@link LoanAccount} entities. Because the
 * schema is normalized, borrower/product are real associations; the derived
 * queries below traverse them by the preserved legacy business keys.
 */
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    /** Finds a loan by the preserved legacy account number ({@code account_number}). */
    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    /** Finds all loans for a borrower, matched via the borrower's legacy {@code external_id}. */
    List<LoanAccount> findByBorrower_ExternalId(String externalId);
}

package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByAccountNumberIn(Collection<String> accountNumbers);

    /**
     * v1 list query. The joins are explicit so the 500k-row list is one statement instead of
     * 2 x N lazy loads; see docs/ARCHITECTURE_ANALYSIS.md section 3.5.
     */
    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product ORDER BY la.id")
    List<LoanAccount> findAllWithBorrowerAndProduct();

    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product"
        + " WHERE la.accountNumber = :accountNumber")
    Optional<LoanAccount> findByAccountNumberWithBorrowerAndProduct(@Param("accountNumber") String accountNumber);

    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product"
        + " WHERE la.borrower.externalId = :externalId ORDER BY la.id")
    List<LoanAccount> findByBorrowerExternalId(@Param("externalId") String externalId);

    Slice<LoanAccount> findAllBy(Pageable pageable);

    /**
     * Keyset page for v2. No {@code JOIN FETCH} here on purpose: combining a fetch join with a row
     * limit makes Hibernate load the whole result set and paginate in memory, which is exactly the
     * failure mode keyset pagination exists to avoid. The associations are filled by batch fetching
     * ({@code hibernate.default_batch_fetch_size}) in one extra statement per association instead.
     */
    @Query("SELECT la FROM LoanAccount la WHERE la.id > :afterId ORDER BY la.id")
    List<LoanAccount> findAfterId(@Param("afterId") Long afterId, Pageable pageable);

    /**
     * Balance update as one conditional statement. The version predicate is what makes it safe under
     * concurrency: if another writer committed after the caller read the account, no row matches,
     * the returned count is zero, and the caller retries with fresh state instead of overwriting a
     * balance it never saw.
     *
     * <p>The new balance is computed by the caller rather than as {@code balance - :amount} in HQL:
     * arithmetic on a bare parameter leaves Hibernate without a precision to cast to and it emits
     * invalid SQL on H2. Correctness is unaffected - the version predicate, not the arithmetic, is
     * what serialises the update.
     */
    // clearAutomatically: a bulk update bypasses the persistence context, so the copy of the account
    // it still holds is stale the moment this runs and must not be flushed back over the new row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LoanAccount la SET la.currentBalance = :newBalance,"
        + " la.version = la.version + 1 WHERE la.id = :id AND la.version = :version")
    int applyPaymentToBalance(@Param("id") Long id,
                              @Param("newBalance") BigDecimal newBalance,
                              @Param("version") Long version);

    /**
     * Reads the account with the row locked, for callers that would rather queue than keep losing an
     * optimistic race. It looks the account up by its natural key in one statement on purpose: a
     * lock taken after an unlocked read returns the persistence context's already-stale copy, so the
     * version would be wrong exactly when it matters.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LoanAccount la WHERE la.accountNumber = :accountNumber")
    Optional<LoanAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}

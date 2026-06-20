package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    // Borrower and product are LAZY; the read path always needs both, so they are
    // fetch-joined here to load each loan summary in a single query (no N+1).

    @Query("select la from LoanAccount la "
            + "join fetch la.borrower join fetch la.product "
            + "where la.accountNumber = :accountNumber")
    Optional<LoanAccount> findByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("select la from LoanAccount la "
            + "join fetch la.borrower join fetch la.product "
            + "order by la.id asc")
    List<LoanAccount> findAllByOrderByIdAsc();

    @Query("select la from LoanAccount la "
            + "join fetch la.borrower b join fetch la.product "
            + "where b.externalId = :externalId order by la.id asc")
    List<LoanAccount> findByBorrower_ExternalIdOrderByIdAsc(@Param("externalId") String externalId);
}

package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    @Query("select a from LoanAccount a join fetch a.borrower join fetch a.product order by a.id")
    List<LoanAccount> findAllWithBorrowerAndProduct();

    @Query("select a from LoanAccount a join fetch a.borrower join fetch a.product "
            + "where a.accountNumber = :accountNumber")
    Optional<LoanAccount> findByAccountNumberWithBorrowerAndProduct(@Param("accountNumber") String accountNumber);

    @Query("select a from LoanAccount a join fetch a.borrower b join fetch a.product "
            + "where b.externalId = :externalId order by a.id")
    List<LoanAccount> findByBorrowerExternalIdWithProduct(@Param("externalId") String externalId);

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByStatus(String status);
}

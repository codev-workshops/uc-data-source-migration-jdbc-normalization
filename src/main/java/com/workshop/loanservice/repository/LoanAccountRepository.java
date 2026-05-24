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

    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product")
    List<LoanAccount> findAllWithBorrowerAndProduct();

    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product WHERE la.accountNumber = :accountNumber")
    Optional<LoanAccount> findByAccountNumberWithDetails(@Param("accountNumber") String accountNumber);

    @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product WHERE la.borrower.externalId = :externalId")
    List<LoanAccount> findByBorrowerExternalIdWithDetails(@Param("externalId") String externalId);
}

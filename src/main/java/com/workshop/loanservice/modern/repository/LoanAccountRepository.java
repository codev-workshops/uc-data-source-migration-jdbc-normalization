package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrowerId(Long borrowerId);

    @Query("SELECT l FROM LoanAccount l JOIN FETCH l.borrower JOIN FETCH l.product ORDER BY l.id")
    List<LoanAccount> findAllWithBorrowerAndProduct();
}

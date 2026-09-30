package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

  Optional<LoanAccount> findByAccountNumber(String accountNumber);

  List<LoanAccount> findByBorrowerId(Long borrowerId);

  @Query("SELECT la FROM LoanAccount la JOIN FETCH la.borrower JOIN FETCH la.product ORDER BY la.id")
  List<LoanAccount> findAllWithBorrowerAndProduct();
}

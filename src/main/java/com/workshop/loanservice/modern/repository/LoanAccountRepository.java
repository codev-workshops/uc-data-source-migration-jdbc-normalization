package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrowerId(Long borrowerId);

    List<LoanAccount> findByStatus(String status);
}

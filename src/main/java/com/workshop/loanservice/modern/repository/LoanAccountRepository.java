package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrower_ExternalId(String externalId);
}

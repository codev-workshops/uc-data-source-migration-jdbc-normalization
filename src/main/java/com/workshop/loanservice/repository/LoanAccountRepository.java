package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrowerExternalId(String borrowerExternalId);

    List<LoanAccount> findByStatus(String status);

    List<LoanAccount> findByProductCode(String productCode);
}

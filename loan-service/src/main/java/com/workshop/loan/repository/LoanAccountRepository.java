package com.workshop.loan.repository;

import com.workshop.loan.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrowerId(String borrowerId);

    List<LoanAccount> findByProductCode(String productCode);

    List<LoanAccount> findByStatus(String status);
}

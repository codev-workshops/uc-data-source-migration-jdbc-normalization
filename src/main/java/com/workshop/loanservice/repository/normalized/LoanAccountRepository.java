package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.LoanAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

  Optional<LoanAccount> findByAccountNumber(String accountNumber);

  List<LoanAccount> findByBorrowerExternalId(String borrowerExternalId);

  List<LoanAccount> findByBorrowerId(Long borrowerId);

  List<LoanAccount> findByStatus(String status);

  List<LoanAccount> findByProductCode(String productCode);
}

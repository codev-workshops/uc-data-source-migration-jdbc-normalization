package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.LoanAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, String> {

  List<LoanAccount> findByBorrowerBorrowerId(String borrowerId);

  List<LoanAccount> findByStatusCode(String statusCode);

  List<LoanAccount> findByLoanProductProductCode(String productCode);
}

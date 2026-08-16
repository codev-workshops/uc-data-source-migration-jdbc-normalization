package com.workshop.loanservice.repository.legacy;

import com.workshop.loanservice.entity.legacy.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);
}

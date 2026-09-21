package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findByBorrowerId(Long borrowerId);

    List<LoanAccount> findByBorrowerExternalId(String externalId);

    List<LoanAccount> findByBorrowerExternalIdOrderByIdAsc(String externalId);

    List<LoanAccount> findAllByOrderByIdAsc();

    List<LoanAccount> findByStatus(String status);

    List<LoanAccount> findByProductCode(String code);
}

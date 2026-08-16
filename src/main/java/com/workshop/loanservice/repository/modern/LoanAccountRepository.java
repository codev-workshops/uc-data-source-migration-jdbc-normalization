package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<LoanAccount> findAllByOrderByIdAsc();

    List<LoanAccount> findByBorrowerExternalIdOrderByIdAsc(String borrowerExternalId);
}

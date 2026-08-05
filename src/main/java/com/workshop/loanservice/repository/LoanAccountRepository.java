package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for the modern {@link LoanAccount} entity.
 */
@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    List<LoanAccount> findAllByOrderByIdAsc();

    List<LoanAccount> findByBorrowerExternalIdOrderByIdAsc(String externalId);

    List<LoanAccount> findByStatus(String status);
}

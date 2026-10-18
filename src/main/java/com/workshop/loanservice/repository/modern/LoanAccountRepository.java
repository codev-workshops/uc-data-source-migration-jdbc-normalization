package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    @EntityGraph(attributePaths = {"borrower", "product"})
    List<LoanAccount> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"borrower", "product"})
    Optional<LoanAccount> findByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = {"borrower", "product"})
    List<LoanAccount> findByBorrowerExternalIdOrderByIdAsc(String borrowerExternalId);
}

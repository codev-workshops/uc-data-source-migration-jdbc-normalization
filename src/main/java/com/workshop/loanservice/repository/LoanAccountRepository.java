package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link LoanAccount}. */
@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

  @EntityGraph(attributePaths = {"borrower", "product"})
  List<LoanAccount> findAllByOrderByIdAsc();

  @EntityGraph(attributePaths = {"borrower", "product"})
  Optional<LoanAccount> findByAccountNumber(String accountNumber);

  @EntityGraph(attributePaths = {"borrower", "product"})
  List<LoanAccount> findByBorrowerExternalIdOrderByIdAsc(String borrowerExternalId);

  List<LoanAccount> findByStatus(String status);

  List<LoanAccount> findByProductCode(String productCode);
}

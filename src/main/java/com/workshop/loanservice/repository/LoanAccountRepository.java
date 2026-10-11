package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

  @Override
  @EntityGraph(attributePaths = {"borrower", "product"})
  List<LoanAccount> findAll(Sort sort);

  @EntityGraph(attributePaths = {"borrower", "product"})
  Optional<LoanAccount> findByAccountNumber(String accountNumber);

  @EntityGraph(attributePaths = {"borrower", "product"})
  List<LoanAccount> findByBorrowerIdOrderByIdAsc(Long borrowerId);

  boolean existsByAccountNumber(String accountNumber);
}

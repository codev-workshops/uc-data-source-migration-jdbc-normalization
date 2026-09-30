package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Borrower;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

  Optional<Borrower> findByLegacyBorrowerId(String legacyBorrowerId);
}

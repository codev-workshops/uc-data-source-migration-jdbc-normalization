package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    Optional<Borrower> findByLegacyBorrowerId(String legacyBorrowerId);
}

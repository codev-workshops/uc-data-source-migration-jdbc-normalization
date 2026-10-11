package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Borrower;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

  Optional<Borrower> findByExternalId(String externalId);

  boolean existsByExternalId(String externalId);
}

package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.Borrower;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

  Optional<Borrower> findByExternalId(String externalId);

  List<Borrower> findByStatus(String status);

  List<Borrower> findByLastNameIgnoreCase(String lastName);
}

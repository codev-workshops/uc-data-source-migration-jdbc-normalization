package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.Borrower;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link Borrower}. */
@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

  Optional<Borrower> findByExternalId(String externalId);

  List<Borrower> findByStatus(String status);

  List<Borrower> findByLastNameIgnoreCase(String lastName);
}

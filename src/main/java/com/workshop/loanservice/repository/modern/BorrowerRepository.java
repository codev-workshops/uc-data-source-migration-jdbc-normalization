package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Borrower;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    Optional<Borrower> findByExternalId(String externalId);

    List<Borrower> findAllByOrderByIdAsc();

    List<Borrower> findByStatus(String status);

    List<Borrower> findByLastNameIgnoreCase(String lastName);
}

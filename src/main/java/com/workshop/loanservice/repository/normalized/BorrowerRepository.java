package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.Borrower;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, String> {

  List<Borrower> findByStatusCode(String statusCode);

  List<Borrower> findByLastNameIgnoreCase(String lastName);
}

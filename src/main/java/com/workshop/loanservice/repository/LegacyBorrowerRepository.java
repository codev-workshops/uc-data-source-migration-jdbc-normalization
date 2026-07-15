package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("legacy-migration")
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {

    List<LegacyBorrower> findByStatusCode(String statusCode);

    List<LegacyBorrower> findByLastNameIgnoreCase(String lastName);
}

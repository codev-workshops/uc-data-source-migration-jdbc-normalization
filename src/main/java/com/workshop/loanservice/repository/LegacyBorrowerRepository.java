package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Legacy CDW-backed type. It is retained only as the source for
 * {@code MigrationService}; the API reads from the modern schema
 * ({@code entity.modern} / {@code repository.modern}). Do not add new usages.
 */
@Deprecated
@Repository
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {

    List<LegacyBorrower> findByStatusCode(String statusCode);

    List<LegacyBorrower> findByLastNameIgnoreCase(String lastName);
}

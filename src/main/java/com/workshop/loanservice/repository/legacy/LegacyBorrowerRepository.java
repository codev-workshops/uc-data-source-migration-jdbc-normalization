package com.workshop.loanservice.repository.legacy;

import com.workshop.loanservice.entity.legacy.LegacyBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated Legacy CDW repository retained only as the source for
 *     {@link com.workshop.loanservice.migration.DataMigrationService}. Use the
 *     repositories in {@code com.workshop.loanservice.repository.modern} instead.
 */
@Deprecated
@Repository
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {

    List<LegacyBorrower> findByStatusCode(String statusCode);

    List<LegacyBorrower> findByLastNameIgnoreCase(String lastName);
}

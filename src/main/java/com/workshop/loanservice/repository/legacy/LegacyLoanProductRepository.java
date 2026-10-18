package com.workshop.loanservice.repository.legacy;

import com.workshop.loanservice.entity.legacy.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Legacy CDW repository retained only as the source for
 *     {@link com.workshop.loanservice.migration.DataMigrationService}. Use the
 *     repositories in {@code com.workshop.loanservice.repository.modern} instead.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

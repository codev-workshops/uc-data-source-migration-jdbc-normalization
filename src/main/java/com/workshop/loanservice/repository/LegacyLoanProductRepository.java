package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Legacy CDW-backed type. It is retained only as the source for
 * {@code MigrationService}; the API reads from the modern schema
 * ({@code entity.modern} / {@code repository.modern}). Do not add new usages.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

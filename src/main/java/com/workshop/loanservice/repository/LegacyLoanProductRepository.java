package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Legacy CDW source, read only by the legacy-to-modern migration
 *     ({@code com.workshop.loanservice.migration}). Remove once the migration is retired.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

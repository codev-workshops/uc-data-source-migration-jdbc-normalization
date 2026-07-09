package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated the modern schema is the operative data source; use
 * {@link com.workshop.loanservice.modern.repository.ModernLoanProductRepository}
 * instead. Retained only for the dual-read fallback
 * ({@code datasource.mode=legacy}) and as the migration source.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Replaced by {@link ModernLoanProductRepository}. Scheduled for removal in Phase 6.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

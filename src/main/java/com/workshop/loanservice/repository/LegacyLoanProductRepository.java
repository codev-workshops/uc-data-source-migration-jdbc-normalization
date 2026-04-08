package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Superseded by {@link LoanProductRepository}. Retained for the
 *             {@link com.workshop.loanservice.service.DataMigrationService}.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

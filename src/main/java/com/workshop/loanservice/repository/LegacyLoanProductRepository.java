package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over the legacy CDW_LN_PROD table.
 *
 * @deprecated backs only the legacy/fallback read path ({@code loanservice.datasource.mode=legacy},
 *     and the dual-read fallback when the modern schema returns nothing) plus the migration's source
 *     reads. The modern replacement is
 *     {@link com.workshop.loanservice.modern.repository.LoanProductRepository}.
 */
@Deprecated
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

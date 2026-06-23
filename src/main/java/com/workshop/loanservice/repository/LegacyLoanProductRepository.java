package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Legacy repository used only by the startup migration as the data source;
 * not part of the API read path.
 */
@Repository
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

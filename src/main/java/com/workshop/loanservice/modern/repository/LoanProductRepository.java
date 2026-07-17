package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for modern {@link LoanProduct} entities. Lookups by the
 * preserved legacy product code {@code PROD_CD} go through {@link #findByCode(String)}.
 */
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    /** Finds a product by the preserved legacy business key ({@code code}). */
    Optional<LoanProduct> findByCode(String code);
}

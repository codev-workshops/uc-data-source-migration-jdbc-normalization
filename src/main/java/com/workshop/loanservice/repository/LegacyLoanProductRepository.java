package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Deprecated // Legacy CDW mapping; retained only as the migration source. Use the modern entities/repositories instead.
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

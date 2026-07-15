package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"legacy-migration", "legacy-migration-run"})
public interface LegacyLoanProductRepository extends JpaRepository<LegacyLoanProduct, String> {
}

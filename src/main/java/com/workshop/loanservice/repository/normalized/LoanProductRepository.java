package com.workshop.loanservice.repository.normalized;

import com.workshop.loanservice.entity.normalized.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, String> {
}

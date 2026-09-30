package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

  Optional<LoanProduct> findByProductCode(String productCode);
}

package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

  Optional<LoanProduct> findByCode(String code);

  boolean existsByCode(String code);
}

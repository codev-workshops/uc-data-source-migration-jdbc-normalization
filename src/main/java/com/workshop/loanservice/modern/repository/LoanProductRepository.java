package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    Optional<LoanProduct> findByCode(String code);
}

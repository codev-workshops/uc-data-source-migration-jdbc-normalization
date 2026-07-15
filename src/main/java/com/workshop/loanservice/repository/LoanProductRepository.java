package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    Optional<LoanProduct> findByCode(String code);

    List<LoanProduct> findAllByOrderByIdAsc();

    boolean existsByCode(String code);
}

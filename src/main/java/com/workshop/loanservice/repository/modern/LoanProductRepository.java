package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    Optional<LoanProduct> findByCode(String code);

    boolean existsByCode(String code);
}

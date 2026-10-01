package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LoanProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link LoanProduct}. */
@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

  Optional<LoanProduct> findByCode(String code);
}

package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @deprecated No longer used by production request paths: {@code LoanService}
 * now reads from the modern normalized schema. Retained because
 * {@code DataMigrationService}, {@code LegacyDtoAssembler} /
 * {@code ValidationController} and any rollback still read the legacy CDW
 * source of record. Do not delete.
 */
@Deprecated
@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);
}

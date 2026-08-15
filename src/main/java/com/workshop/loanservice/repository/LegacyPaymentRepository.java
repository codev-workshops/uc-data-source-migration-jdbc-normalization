package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
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
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(String loanAccountNumber);
}

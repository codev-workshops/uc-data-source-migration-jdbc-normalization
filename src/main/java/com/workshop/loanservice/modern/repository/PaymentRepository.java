package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByLegacyId(String legacyId);

    List<Payment> findByLegacyIdIn(Collection<String> legacyIds);

    /**
     * v1 payment history. Ordered by the typed date, then by id so the order is deterministic when
     * several payments share a date.
     *
     * <p>Legacy sorted the {@code MM/DD/YYYY} <em>string</em>, which only coincides with date order
     * inside a single year. Sorting the real date is the correct behaviour and matches legacy on all
     * existing data; the divergence is recorded in docs/MIGRATION_NOTES.md.
     */
    @Query("SELECT p FROM Payment p JOIN FETCH p.loanAccount la WHERE la.accountNumber = :accountNumber"
        + " ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByAccountNumberOrderByDateDesc(@Param("accountNumber") String accountNumber);

    @Query("SELECT p FROM Payment p JOIN FETCH p.loanAccount la WHERE la.accountNumber = :accountNumber"
        + " ORDER BY p.paymentDate DESC, p.id DESC")
    Slice<Payment> findByAccountNumberOrderByDateDesc(@Param("accountNumber") String accountNumber,
                                                      Pageable pageable);
}

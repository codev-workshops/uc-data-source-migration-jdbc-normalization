package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for modern {@link Borrower} entities (numeric surrogate
 * PK). Lookups by the preserved legacy business key {@code BORR_ID} go through
 * {@link #findByExternalId(String)}.
 */
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    /** Finds a borrower by the preserved legacy business key ({@code external_id}). */
    Optional<Borrower> findByExternalId(String externalId);
}

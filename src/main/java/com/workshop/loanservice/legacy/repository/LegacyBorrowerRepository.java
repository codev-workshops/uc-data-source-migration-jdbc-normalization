package com.workshop.loanservice.legacy.repository;

import com.workshop.loanservice.legacy.entity.LegacyBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @deprecated Legacy CDW repository. Used only by the data migration and the
 * {@code LEGACY} dual-read fallback; prefer the modern repositories.
 */
@Deprecated
@Repository
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {
}

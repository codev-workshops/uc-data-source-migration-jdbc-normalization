package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository over the legacy CDW_BORR_MSTR table.
 *
 * @deprecated backs only the legacy/fallback read path ({@code loanservice.datasource.mode=legacy},
 *     and the dual-read fallback when the modern schema returns nothing) plus the migration's source
 *     reads. The modern replacement is
 *     {@link com.workshop.loanservice.modern.repository.BorrowerRepository}.
 */
@Deprecated
@Repository
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {

    List<LegacyBorrower> findByStatusCode(String statusCode);

    List<LegacyBorrower> findByLastNameIgnoreCase(String lastName);
}

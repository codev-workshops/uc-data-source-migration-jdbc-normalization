package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Borrower;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    Optional<Borrower> findByExternalId(String externalId);

    List<Borrower> findByExternalIdIn(Collection<String> externalIds);

    /** v2 offset pagination. {@code Slice} avoids the {@code COUNT(*)} unless the caller asks. */
    Slice<Borrower> findAllBy(Pageable pageable);

    /** v2 keyset pagination: seek by primary key instead of {@code OFFSET}. */
    @Query("SELECT b FROM Borrower b WHERE b.id > :afterId ORDER BY b.id")
    List<Borrower> findAfterId(@Param("afterId") Long afterId, Pageable pageable);
}

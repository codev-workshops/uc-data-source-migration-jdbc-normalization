package com.workshop.loanservice.repository;

import jakarta.persistence.QueryHint;
import org.hibernate.jpa.AvailableHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.stream.Stream;

/**
 * A single streamed scan of a legacy table, for the migration to consume in bounded chunks.
 *
 * <p>Offset paging is the obvious way to chunk a backfill and the wrong one: {@code OFFSET 1900000}
 * makes the database count and discard 1.9 million rows before returning the next 2,000, so a 2M-row
 * payment table costs O(n²) scan work and the run grinds to a halt near the end. Measured here, the
 * final chunks of a 500k-loan backfill took seconds each.
 *
 * <p>Keyset paging fixes the cost but changes the order to key order, and the frozen v1 list
 * endpoints return rows in the warehouse's physical scan order. One unordered streamed scan keeps
 * both: O(n), and the same row order the legacy API has always produced.
 */
@NoRepositoryBean
public interface LegacyChunkSource<T> extends JpaRepository<T, String> {

    @Query("SELECT e FROM #{#entityName} e")
    @QueryHints(@QueryHint(name = AvailableHints.HINT_FETCH_SIZE, value = "1000"))
    Stream<T> streamAll();
}

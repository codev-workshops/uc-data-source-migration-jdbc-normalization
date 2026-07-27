package com.workshop.loanservice.migration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Legacy id to modern id tracking table, living in the modern datasource.
 *
 * <p>The table is deliberately not part of {@code data/modern-schema/modern_tables.sql}: it is a
 * migration artefact, created on demand by {@link #createTableIfMissing()}. Reads and writes go
 * through the modern {@link EntityManager}, so they join whatever
 * {@code modernTransactionManager} transaction is active and roll back with the migrated data.
 */
@Component
public class MigrationIdMap {

    public static final String BORROWER = "borrower";
    public static final String LOAN_PRODUCT = "loan_product";
    public static final String LOAN_ACCOUNT = "loan_account";
    public static final String PAYMENT = "payment";

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS migration_id_map (
                id            BIGINT PRIMARY KEY AUTO_INCREMENT,
                entity_type   VARCHAR(50) NOT NULL,
                legacy_id     VARCHAR(100) NOT NULL,
                modern_id     BIGINT NOT NULL,
                migrated_at   TIMESTAMP NOT NULL,
                UNIQUE(entity_type, legacy_id)
            )""";

    private final JdbcTemplate modernJdbcTemplate;

    @PersistenceContext(unitName = "modern")
    private EntityManager entityManager;

    public MigrationIdMap(@Qualifier("modernDataSource") DataSource modernDataSource) {
        this.modernJdbcTemplate = new JdbcTemplate(modernDataSource);
    }

    /**
     * Creates the tracking table if it does not exist yet. Runs on its own connection, outside the
     * migration transaction, so the DDL (auto-committed by every database we target) cannot affect
     * the rollback semantics of the data migration.
     */
    public void createTableIfMissing() {
        modernJdbcTemplate.execute(CREATE_TABLE);
    }

    public Optional<Long> findModernId(String entityType, String legacyId) {
        Query query = entityManager.createNativeQuery(
                "SELECT modern_id FROM migration_id_map WHERE entity_type = ?1 AND legacy_id = ?2");
        query.setParameter(1, entityType);
        query.setParameter(2, legacyId);
        List<?> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(((Number) rows.get(0)).longValue());
    }

    public boolean exists(String entityType, String legacyId) {
        return findModernId(entityType, legacyId).isPresent();
    }

    /** Reverse of {@link #findModernId}: the preserved legacy id for a migrated modern row. */
    public Optional<String> findLegacyId(String entityType, Long modernId) {
        Query query = entityManager.createNativeQuery(
                "SELECT legacy_id FROM migration_id_map WHERE entity_type = ?1 AND modern_id = ?2");
        query.setParameter(1, entityType);
        query.setParameter(2, modernId);
        List<?> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((String) rows.get(0));
    }

    public void record(String entityType, String legacyId, Long modernId, LocalDateTime migratedAt) {
        Query query = entityManager.createNativeQuery(
                "INSERT INTO migration_id_map (entity_type, legacy_id, modern_id, migrated_at)"
                        + " VALUES (?1, ?2, ?3, ?4)");
        query.setParameter(1, entityType);
        query.setParameter(2, legacyId);
        query.setParameter(3, modernId);
        query.setParameter(4, migratedAt);
        query.executeUpdate();
    }

    /** Number of legacy records of {@code entityType} mapped to a modern row. */
    public long count(String entityType) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM migration_id_map WHERE entity_type = ?1");
        query.setParameter(1, entityType);
        return ((Number) query.getSingleResult()).longValue();
    }
}

package com.onboarding.diary.repository;

import com.onboarding.diary.entity.IssueLog;
import com.onboarding.diary.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueLogRepository extends JpaRepository<IssueLog, UUID> {
    List<IssueLog> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, IssueStatus status);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS wk, COUNT(*) AS cnt "
            + "FROM issue_logs WHERE user_id = :userId GROUP BY wk ORDER BY wk", nativeQuery = true)
    List<Object[]> weeklyCountsByUser(@Param("userId") UUID userId);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS wk, COUNT(*) AS cnt "
            + "FROM issue_logs GROUP BY wk ORDER BY wk", nativeQuery = true)
    List<Object[]> weeklyCountsAll();
}

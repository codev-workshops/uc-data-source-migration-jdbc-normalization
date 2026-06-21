package com.onboarding.diary.repository;

import com.onboarding.diary.entity.TaskLog;
import com.onboarding.diary.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, UUID> {
    List<TaskLog> findByUserId(UUID userId);

    List<TaskLog> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, TaskStatus status);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS wk, COUNT(*) AS cnt "
            + "FROM task_logs WHERE user_id = :userId GROUP BY wk ORDER BY wk", nativeQuery = true)
    List<Object[]> weeklyCountsByUser(@Param("userId") UUID userId);

    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', created_at), 'YYYY-MM-DD') AS wk, COUNT(*) AS cnt "
            + "FROM task_logs GROUP BY wk ORDER BY wk", nativeQuery = true)
    List<Object[]> weeklyCountsAll();
}

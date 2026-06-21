package com.onboarding.diary.service;

import com.onboarding.diary.dto.DashboardSummary;
import com.onboarding.diary.dto.WeeklyStat;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.IssueStatus;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.enums.TaskStatus;
import com.onboarding.diary.repository.AdditionalNoteRepository;
import com.onboarding.diary.repository.FeedbackNoteRepository;
import com.onboarding.diary.repository.IssueLogRepository;
import com.onboarding.diary.repository.TaskLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final TaskLogRepository taskLogRepository;
    private final IssueLogRepository issueLogRepository;
    private final FeedbackNoteRepository feedbackNoteRepository;
    private final AdditionalNoteRepository additionalNoteRepository;

    public DashboardService(TaskLogRepository taskLogRepository,
                            IssueLogRepository issueLogRepository,
                            FeedbackNoteRepository feedbackNoteRepository,
                            AdditionalNoteRepository additionalNoteRepository) {
        this.taskLogRepository = taskLogRepository;
        this.issueLogRepository = issueLogRepository;
        this.feedbackNoteRepository = feedbackNoteRepository;
        this.additionalNoteRepository = additionalNoteRepository;
    }

    public DashboardSummary getSummary(User currentUser) {
        UUID userId = currentUser.getId();
        if (currentUser.getRole() == Role.RECRUIT) {
            return DashboardSummary.builder()
                    .totalTasks(taskLogRepository.countByUserId(userId))
                    .completedTasks(taskLogRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED))
                    .openIssues(issueLogRepository.countByUserIdAndStatus(userId, IssueStatus.OPEN))
                    .resolvedIssues(issueLogRepository.countByUserIdAndStatus(userId, IssueStatus.RESOLVED))
                    .feedbackCount(feedbackNoteRepository.countByRecruitId(userId))
                    .notesCount(additionalNoteRepository.countByUserId(userId))
                    .build();
        }
        long totalTasks = taskLogRepository.count();
        long completedTasks = taskLogRepository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long openIssues = issueLogRepository.findAll().stream()
                .filter(i -> i.getStatus() == IssueStatus.OPEN).count();
        long resolvedIssues = issueLogRepository.findAll().stream()
                .filter(i -> i.getStatus() == IssueStatus.RESOLVED).count();
        return DashboardSummary.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .openIssues(openIssues)
                .resolvedIssues(resolvedIssues)
                .feedbackCount(feedbackNoteRepository.count())
                .notesCount(additionalNoteRepository.count())
                .build();
    }

    public List<WeeklyStat> getWeeklyStats(User currentUser) {
        boolean scoped = currentUser.getRole() == Role.RECRUIT;
        List<Object[]> taskRows = scoped
                ? taskLogRepository.weeklyCountsByUser(currentUser.getId())
                : taskLogRepository.weeklyCountsAll();
        List<Object[]> issueRows = scoped
                ? issueLogRepository.weeklyCountsByUser(currentUser.getId())
                : issueLogRepository.weeklyCountsAll();

        Map<String, WeeklyStat> byWeek = new LinkedHashMap<>();
        for (Object[] row : taskRows) {
            String week = (String) row[0];
            long count = ((Number) row[1]).longValue();
            byWeek.computeIfAbsent(week, w -> WeeklyStat.builder().week(w).build()).setTasks(count);
        }
        for (Object[] row : issueRows) {
            String week = (String) row[0];
            long count = ((Number) row[1]).longValue();
            byWeek.computeIfAbsent(week, w -> WeeklyStat.builder().week(w).build()).setIssues(count);
        }
        List<WeeklyStat> stats = new ArrayList<>(byWeek.values());
        stats.sort((a, b) -> {
            if (a.getWeek() == null) {
                return -1;
            }
            if (b.getWeek() == null) {
                return 1;
            }
            return a.getWeek().compareTo(b.getWeek());
        });
        return stats;
    }
}

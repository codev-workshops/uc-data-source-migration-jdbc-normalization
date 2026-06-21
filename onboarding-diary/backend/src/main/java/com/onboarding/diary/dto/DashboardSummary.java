package com.onboarding.diary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummary {
    private long totalTasks;
    private long completedTasks;
    private long openIssues;
    private long resolvedIssues;
    private long feedbackCount;
    private long notesCount;
}

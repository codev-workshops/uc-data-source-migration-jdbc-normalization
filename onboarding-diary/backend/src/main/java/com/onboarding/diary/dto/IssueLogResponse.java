package com.onboarding.diary.dto;

import com.onboarding.diary.enums.IssueSeverity;
import com.onboarding.diary.enums.IssueStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueLogResponse {
    private UUID id;
    private UUID userId;
    private String title;
    private String description;
    private IssueSeverity severity;
    private IssueStatus status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

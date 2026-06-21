package com.onboarding.diary.dto;

import com.onboarding.diary.enums.IssueSeverity;
import com.onboarding.diary.enums.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueLogRequest {
    @NotBlank
    private String title;

    private String description;
    private IssueSeverity severity;
    private IssueStatus status;
    private String resolution;
}

package com.onboarding.diary.dto;

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
public class FeedbackNoteResponse {
    private UUID id;
    private UUID recruitId;
    private String recruitName;
    private UUID managerId;
    private String managerName;
    private String content;
    private Integer week;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

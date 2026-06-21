package com.onboarding.diary.dto;

import com.onboarding.diary.enums.Priority;
import com.onboarding.diary.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskLogRequest {
    @NotBlank
    private String title;

    private String description;
    private TaskStatus status;
    private Priority priority;
    private LocalDate dueDate;
}

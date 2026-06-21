package com.onboarding.diary.service;

import com.onboarding.diary.dto.TaskLogRequest;
import com.onboarding.diary.dto.TaskLogResponse;
import com.onboarding.diary.entity.TaskLog;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.Priority;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.enums.SourceType;
import com.onboarding.diary.enums.TaskStatus;
import com.onboarding.diary.exception.ResourceNotFoundException;
import com.onboarding.diary.repository.TaskLogRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TaskLogService {

    private final TaskLogRepository taskLogRepository;
    private final SemanticSearchService semanticSearchService;

    public TaskLogService(TaskLogRepository taskLogRepository, SemanticSearchService semanticSearchService) {
        this.taskLogRepository = taskLogRepository;
        this.semanticSearchService = semanticSearchService;
    }

    public List<TaskLogResponse> getAll(User currentUser) {
        List<TaskLog> logs = currentUser.getRole() == Role.RECRUIT
                ? taskLogRepository.findByUserId(currentUser.getId())
                : taskLogRepository.findAll();
        return logs.stream().map(this::toResponse).toList();
    }

    public TaskLogResponse getById(UUID id, User currentUser) {
        return toResponse(findOwned(id, currentUser));
    }

    public TaskLogResponse create(TaskLogRequest request, User currentUser) {
        TaskLog log = TaskLog.builder()
                .user(currentUser)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() == null ? TaskStatus.PENDING : request.getStatus())
                .priority(request.getPriority() == null ? Priority.MEDIUM : request.getPriority())
                .dueDate(request.getDueDate())
                .build();
        if (log.getStatus() == TaskStatus.COMPLETED) {
            log.setCompletedAt(LocalDateTime.now());
        }
        log = taskLogRepository.save(log);
        semanticSearchService.indexLog(SourceType.TASK, log.getId(), buildContent(log));
        return toResponse(log);
    }

    public TaskLogResponse update(UUID id, TaskLogRequest request, User currentUser) {
        TaskLog log = findOwned(id, currentUser);
        log.setTitle(request.getTitle());
        log.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            log.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            log.setPriority(request.getPriority());
        }
        log.setDueDate(request.getDueDate());
        if (log.getStatus() == TaskStatus.COMPLETED && log.getCompletedAt() == null) {
            log.setCompletedAt(LocalDateTime.now());
        } else if (log.getStatus() != TaskStatus.COMPLETED) {
            log.setCompletedAt(null);
        }
        log = taskLogRepository.save(log);
        semanticSearchService.indexLog(SourceType.TASK, log.getId(), buildContent(log));
        return toResponse(log);
    }

    public void delete(UUID id, User currentUser) {
        TaskLog log = findOwned(id, currentUser);
        taskLogRepository.delete(log);
    }

    private TaskLog findOwned(UUID id, User currentUser) {
        TaskLog log = taskLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task log not found: " + id));
        if (currentUser.getRole() == Role.RECRUIT
                && !log.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Not allowed to access this task log");
        }
        return log;
    }

    private String buildContent(TaskLog log) {
        return log.getTitle() + " " + (log.getDescription() == null ? "" : log.getDescription());
    }

    private TaskLogResponse toResponse(TaskLog log) {
        return TaskLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser() == null ? null : log.getUser().getId())
                .title(log.getTitle())
                .description(log.getDescription())
                .status(log.getStatus())
                .priority(log.getPriority())
                .dueDate(log.getDueDate())
                .completedAt(log.getCompletedAt())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}

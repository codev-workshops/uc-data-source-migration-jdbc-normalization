package com.onboarding.diary.service;

import com.onboarding.diary.dto.IssueLogRequest;
import com.onboarding.diary.dto.IssueLogResponse;
import com.onboarding.diary.entity.IssueLog;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.IssueSeverity;
import com.onboarding.diary.enums.IssueStatus;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.enums.SourceType;
import com.onboarding.diary.exception.ResourceNotFoundException;
import com.onboarding.diary.repository.IssueLogRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class IssueLogService {

    private final IssueLogRepository issueLogRepository;
    private final SemanticSearchService semanticSearchService;

    public IssueLogService(IssueLogRepository issueLogRepository, SemanticSearchService semanticSearchService) {
        this.issueLogRepository = issueLogRepository;
        this.semanticSearchService = semanticSearchService;
    }

    public List<IssueLogResponse> getAll(User currentUser) {
        List<IssueLog> logs = currentUser.getRole() == Role.RECRUIT
                ? issueLogRepository.findByUserId(currentUser.getId())
                : issueLogRepository.findAll();
        return logs.stream().map(this::toResponse).toList();
    }

    public IssueLogResponse getById(UUID id, User currentUser) {
        return toResponse(findOwned(id, currentUser));
    }

    public IssueLogResponse create(IssueLogRequest request, User currentUser) {
        IssueLog log = IssueLog.builder()
                .user(currentUser)
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(request.getSeverity() == null ? IssueSeverity.MEDIUM : request.getSeverity())
                .status(request.getStatus() == null ? IssueStatus.OPEN : request.getStatus())
                .resolution(request.getResolution())
                .build();
        log = issueLogRepository.save(log);
        semanticSearchService.indexLog(SourceType.ISSUE, log.getId(), buildContent(log));
        return toResponse(log);
    }

    public IssueLogResponse update(UUID id, IssueLogRequest request, User currentUser) {
        IssueLog log = findOwned(id, currentUser);
        log.setTitle(request.getTitle());
        log.setDescription(request.getDescription());
        if (request.getSeverity() != null) {
            log.setSeverity(request.getSeverity());
        }
        if (request.getStatus() != null) {
            log.setStatus(request.getStatus());
        }
        log.setResolution(request.getResolution());
        log = issueLogRepository.save(log);
        semanticSearchService.indexLog(SourceType.ISSUE, log.getId(), buildContent(log));
        return toResponse(log);
    }

    public void delete(UUID id, User currentUser) {
        IssueLog log = findOwned(id, currentUser);
        issueLogRepository.delete(log);
    }

    private IssueLog findOwned(UUID id, User currentUser) {
        IssueLog log = issueLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue log not found: " + id));
        if (currentUser.getRole() == Role.RECRUIT
                && !log.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Not allowed to access this issue log");
        }
        return log;
    }

    private String buildContent(IssueLog log) {
        return log.getTitle() + " "
                + (log.getDescription() == null ? "" : log.getDescription()) + " "
                + (log.getResolution() == null ? "" : log.getResolution());
    }

    private IssueLogResponse toResponse(IssueLog log) {
        return IssueLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser() == null ? null : log.getUser().getId())
                .title(log.getTitle())
                .description(log.getDescription())
                .severity(log.getSeverity())
                .status(log.getStatus())
                .resolution(log.getResolution())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}

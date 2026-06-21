package com.onboarding.diary.controller;

import com.onboarding.diary.dto.IssueLogRequest;
import com.onboarding.diary.dto.IssueLogResponse;
import com.onboarding.diary.security.UserPrincipal;
import com.onboarding.diary.service.IssueLogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issue-logs")
public class IssueLogController {

    private final IssueLogService issueLogService;

    public IssueLogController(IssueLogService issueLogService) {
        this.issueLogService = issueLogService;
    }

    @GetMapping
    public ResponseEntity<List<IssueLogResponse>> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(issueLogService.getAll(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueLogResponse> getById(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(issueLogService.getById(id, principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<IssueLogResponse> create(@Valid @RequestBody IssueLogRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(issueLogService.create(request, principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IssueLogResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody IssueLogRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(issueLogService.update(id, request, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        issueLogService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}

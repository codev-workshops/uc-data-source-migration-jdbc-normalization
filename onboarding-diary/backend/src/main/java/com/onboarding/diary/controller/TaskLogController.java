package com.onboarding.diary.controller;

import com.onboarding.diary.dto.TaskLogRequest;
import com.onboarding.diary.dto.TaskLogResponse;
import com.onboarding.diary.security.UserPrincipal;
import com.onboarding.diary.service.TaskLogService;
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
@RequestMapping("/api/task-logs")
public class TaskLogController {

    private final TaskLogService taskLogService;

    public TaskLogController(TaskLogService taskLogService) {
        this.taskLogService = taskLogService;
    }

    @GetMapping
    public ResponseEntity<List<TaskLogResponse>> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskLogService.getAll(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskLogResponse> getById(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskLogService.getById(id, principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<TaskLogResponse> create(@Valid @RequestBody TaskLogRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskLogService.create(request, principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskLogResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody TaskLogRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskLogService.update(id, request, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        taskLogService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}

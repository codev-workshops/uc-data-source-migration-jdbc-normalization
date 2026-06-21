package com.onboarding.diary.controller;

import com.onboarding.diary.dto.FeedbackNoteRequest;
import com.onboarding.diary.dto.FeedbackNoteResponse;
import com.onboarding.diary.security.UserPrincipal;
import com.onboarding.diary.service.FeedbackNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback-notes")
public class FeedbackNoteController {

    private final FeedbackNoteService feedbackNoteService;

    public FeedbackNoteController(FeedbackNoteService feedbackNoteService) {
        this.feedbackNoteService = feedbackNoteService;
    }

    @GetMapping
    public ResponseEntity<List<FeedbackNoteResponse>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID recruitId) {
        if (recruitId != null) {
            return ResponseEntity.ok(feedbackNoteService.getByRecruitId(recruitId));
        }
        return ResponseEntity.ok(feedbackNoteService.getAll(principal.getUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<FeedbackNoteResponse> create(@Valid @RequestBody FeedbackNoteRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(feedbackNoteService.create(request, principal.getUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<FeedbackNoteResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody FeedbackNoteRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(feedbackNoteService.update(id, request, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        feedbackNoteService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}

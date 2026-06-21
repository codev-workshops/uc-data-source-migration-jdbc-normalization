package com.onboarding.diary.controller;

import com.onboarding.diary.dto.AdditionalNoteRequest;
import com.onboarding.diary.dto.AdditionalNoteResponse;
import com.onboarding.diary.security.UserPrincipal;
import com.onboarding.diary.service.AdditionalNoteService;
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
@RequestMapping("/api/notes")
public class AdditionalNoteController {

    private final AdditionalNoteService additionalNoteService;

    public AdditionalNoteController(AdditionalNoteService additionalNoteService) {
        this.additionalNoteService = additionalNoteService;
    }

    @GetMapping
    public ResponseEntity<List<AdditionalNoteResponse>> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(additionalNoteService.getAll(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdditionalNoteResponse> getById(@PathVariable UUID id,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(additionalNoteService.getById(id, principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<AdditionalNoteResponse> create(@Valid @RequestBody AdditionalNoteRequest request,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(additionalNoteService.create(request, principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdditionalNoteResponse> update(@PathVariable UUID id,
                                                         @Valid @RequestBody AdditionalNoteRequest request,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(additionalNoteService.update(id, request, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        additionalNoteService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}

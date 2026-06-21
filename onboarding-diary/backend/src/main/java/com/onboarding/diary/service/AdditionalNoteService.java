package com.onboarding.diary.service;

import com.onboarding.diary.dto.AdditionalNoteRequest;
import com.onboarding.diary.dto.AdditionalNoteResponse;
import com.onboarding.diary.entity.AdditionalNote;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.enums.SourceType;
import com.onboarding.diary.exception.ResourceNotFoundException;
import com.onboarding.diary.repository.AdditionalNoteRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdditionalNoteService {

    private final AdditionalNoteRepository additionalNoteRepository;
    private final SemanticSearchService semanticSearchService;

    public AdditionalNoteService(AdditionalNoteRepository additionalNoteRepository,
                                 SemanticSearchService semanticSearchService) {
        this.additionalNoteRepository = additionalNoteRepository;
        this.semanticSearchService = semanticSearchService;
    }

    public List<AdditionalNoteResponse> getAll(User currentUser) {
        List<AdditionalNote> notes = currentUser.getRole() == Role.RECRUIT
                ? additionalNoteRepository.findByUserId(currentUser.getId())
                : additionalNoteRepository.findAll();
        return notes.stream().map(this::toResponse).toList();
    }

    public AdditionalNoteResponse getById(UUID id, User currentUser) {
        return toResponse(findOwned(id, currentUser));
    }

    public AdditionalNoteResponse create(AdditionalNoteRequest request, User currentUser) {
        AdditionalNote note = AdditionalNote.builder()
                .user(currentUser)
                .title(request.getTitle())
                .content(request.getContent())
                .category(request.getCategory())
                .build();
        note = additionalNoteRepository.save(note);
        semanticSearchService.indexLog(SourceType.NOTE, note.getId(), buildContent(note));
        return toResponse(note);
    }

    public AdditionalNoteResponse update(UUID id, AdditionalNoteRequest request, User currentUser) {
        AdditionalNote note = findOwned(id, currentUser);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setCategory(request.getCategory());
        note = additionalNoteRepository.save(note);
        semanticSearchService.indexLog(SourceType.NOTE, note.getId(), buildContent(note));
        return toResponse(note);
    }

    public void delete(UUID id, User currentUser) {
        AdditionalNote note = findOwned(id, currentUser);
        additionalNoteRepository.delete(note);
    }

    private AdditionalNote findOwned(UUID id, User currentUser) {
        AdditionalNote note = additionalNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + id));
        if (currentUser.getRole() == Role.RECRUIT
                && !note.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Not allowed to access this note");
        }
        return note;
    }

    private String buildContent(AdditionalNote note) {
        return (note.getTitle() == null ? "" : note.getTitle()) + " "
                + (note.getContent() == null ? "" : note.getContent());
    }

    private AdditionalNoteResponse toResponse(AdditionalNote note) {
        return AdditionalNoteResponse.builder()
                .id(note.getId())
                .userId(note.getUser() == null ? null : note.getUser().getId())
                .title(note.getTitle())
                .content(note.getContent())
                .category(note.getCategory())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}

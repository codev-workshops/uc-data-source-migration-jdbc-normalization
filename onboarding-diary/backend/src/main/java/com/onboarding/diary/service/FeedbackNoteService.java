package com.onboarding.diary.service;

import com.onboarding.diary.dto.FeedbackNoteRequest;
import com.onboarding.diary.dto.FeedbackNoteResponse;
import com.onboarding.diary.entity.FeedbackNote;
import com.onboarding.diary.entity.User;
import com.onboarding.diary.enums.Role;
import com.onboarding.diary.enums.SourceType;
import com.onboarding.diary.exception.BadRequestException;
import com.onboarding.diary.exception.ResourceNotFoundException;
import com.onboarding.diary.repository.FeedbackNoteRepository;
import com.onboarding.diary.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FeedbackNoteService {

    private final FeedbackNoteRepository feedbackNoteRepository;
    private final UserRepository userRepository;
    private final SemanticSearchService semanticSearchService;

    public FeedbackNoteService(FeedbackNoteRepository feedbackNoteRepository,
                               UserRepository userRepository,
                               SemanticSearchService semanticSearchService) {
        this.feedbackNoteRepository = feedbackNoteRepository;
        this.userRepository = userRepository;
        this.semanticSearchService = semanticSearchService;
    }

    public List<FeedbackNoteResponse> getAll(User currentUser) {
        List<FeedbackNote> notes = switch (currentUser.getRole()) {
            case RECRUIT -> feedbackNoteRepository.findByRecruitId(currentUser.getId());
            case MANAGER -> feedbackNoteRepository.findByManagerId(currentUser.getId());
            case ADMIN -> feedbackNoteRepository.findAll();
        };
        return notes.stream().map(this::toResponse).toList();
    }

    public List<FeedbackNoteResponse> getByRecruitId(UUID recruitId) {
        return feedbackNoteRepository.findByRecruitId(recruitId).stream().map(this::toResponse).toList();
    }

    public FeedbackNoteResponse create(FeedbackNoteRequest request, User currentUser) {
        requireManagerOrAdmin(currentUser);
        User recruit = userRepository.findById(request.getRecruitId())
                .orElseThrow(() -> new ResourceNotFoundException("Recruit not found: " + request.getRecruitId()));
        if (recruit.getRole() != Role.RECRUIT) {
            throw new BadRequestException("Target user is not a recruit");
        }
        FeedbackNote note = FeedbackNote.builder()
                .recruit(recruit)
                .manager(currentUser)
                .content(request.getContent())
                .week(request.getWeek())
                .build();
        note = feedbackNoteRepository.save(note);
        semanticSearchService.indexLog(SourceType.FEEDBACK, note.getId(), note.getContent());
        return toResponse(note);
    }

    public FeedbackNoteResponse update(UUID id, FeedbackNoteRequest request, User currentUser) {
        requireManagerOrAdmin(currentUser);
        FeedbackNote note = feedbackNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback note not found: " + id));
        if (currentUser.getRole() == Role.MANAGER
                && !note.getManager().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Not allowed to modify this feedback note");
        }
        if (request.getRecruitId() != null) {
            User recruit = userRepository.findById(request.getRecruitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recruit not found: " + request.getRecruitId()));
            note.setRecruit(recruit);
        }
        note.setContent(request.getContent());
        note.setWeek(request.getWeek());
        note = feedbackNoteRepository.save(note);
        semanticSearchService.indexLog(SourceType.FEEDBACK, note.getId(), note.getContent());
        return toResponse(note);
    }

    public void delete(UUID id, User currentUser) {
        requireManagerOrAdmin(currentUser);
        FeedbackNote note = feedbackNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback note not found: " + id));
        if (currentUser.getRole() == Role.MANAGER
                && !note.getManager().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Not allowed to delete this feedback note");
        }
        feedbackNoteRepository.delete(note);
    }

    private void requireManagerOrAdmin(User currentUser) {
        if (currentUser.getRole() == Role.RECRUIT) {
            throw new AccessDeniedException("Only managers and admins can manage feedback notes");
        }
    }

    private FeedbackNoteResponse toResponse(FeedbackNote note) {
        return FeedbackNoteResponse.builder()
                .id(note.getId())
                .recruitId(note.getRecruit() == null ? null : note.getRecruit().getId())
                .recruitName(note.getRecruit() == null ? null : note.getRecruit().getFullName())
                .managerId(note.getManager() == null ? null : note.getManager().getId())
                .managerName(note.getManager() == null ? null : note.getManager().getFullName())
                .content(note.getContent())
                .week(note.getWeek())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}

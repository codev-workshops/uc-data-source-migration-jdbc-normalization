package com.onboarding.diary.repository;

import com.onboarding.diary.entity.FeedbackNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackNoteRepository extends JpaRepository<FeedbackNote, UUID> {
    List<FeedbackNote> findByRecruitId(UUID recruitId);

    List<FeedbackNote> findByManagerId(UUID managerId);

    long countByRecruitId(UUID recruitId);
}

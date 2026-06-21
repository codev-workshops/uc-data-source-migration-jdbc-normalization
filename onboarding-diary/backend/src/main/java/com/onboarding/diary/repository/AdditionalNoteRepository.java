package com.onboarding.diary.repository;

import com.onboarding.diary.entity.AdditionalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdditionalNoteRepository extends JpaRepository<AdditionalNote, UUID> {
    List<AdditionalNote> findByUserId(UUID userId);

    long countByUserId(UUID userId);
}

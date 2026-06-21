package com.onboarding.diary.dto;

import com.onboarding.diary.enums.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResult {
    private SourceType sourceType;
    private UUID sourceId;
    private String content;
    private double score;
}

package com.onboarding.diary.dto;

import com.onboarding.diary.enums.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {
    private String query;
    private List<SourceType> sourceTypes;
}

package com.onboarding.diary.controller;

import com.onboarding.diary.dto.SearchRequest;
import com.onboarding.diary.dto.SearchResult;
import com.onboarding.diary.service.AIService;
import com.onboarding.diary.service.SemanticSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;
    private final SemanticSearchService semanticSearchService;

    public AIController(AIService aiService, SemanticSearchService semanticSearchService) {
        this.aiService = aiService;
        this.semanticSearchService = semanticSearchService;
    }

    @GetMapping("/weekly-summary")
    public ResponseEntity<Map<String, Object>> weeklySummary(
            @RequestParam UUID recruitId,
            @RequestParam int week) {
        String summary = aiService.generateWeeklySummary(recruitId, week);
        return ResponseEntity.ok(Map.of("recruitId", recruitId, "week", week, "summary", summary));
    }

    @PostMapping("/semantic-search")
    public ResponseEntity<List<SearchResult>> semanticSearch(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(semanticSearchService.search(request));
    }
}

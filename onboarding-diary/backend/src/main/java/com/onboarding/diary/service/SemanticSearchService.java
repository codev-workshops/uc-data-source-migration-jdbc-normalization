package com.onboarding.diary.service;

import com.onboarding.diary.dto.SearchRequest;
import com.onboarding.diary.dto.SearchResult;
import com.onboarding.diary.entity.AdditionalNote;
import com.onboarding.diary.entity.FeedbackNote;
import com.onboarding.diary.entity.IssueLog;
import com.onboarding.diary.entity.TaskLog;
import com.onboarding.diary.enums.SourceType;
import com.onboarding.diary.repository.AdditionalNoteRepository;
import com.onboarding.diary.repository.FeedbackNoteRepository;
import com.onboarding.diary.repository.IssueLogRepository;
import com.onboarding.diary.repository.TaskLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SemanticSearchService {

    private final TaskLogRepository taskLogRepository;
    private final IssueLogRepository issueLogRepository;
    private final FeedbackNoteRepository feedbackNoteRepository;
    private final AdditionalNoteRepository additionalNoteRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.embedding-model}")
    private String embeddingModel;

    private final Map<String, float[]> embeddingStore = new ConcurrentHashMap<>();
    private final Map<String, String> contentStore = new ConcurrentHashMap<>();

    public SemanticSearchService(TaskLogRepository taskLogRepository,
                                 IssueLogRepository issueLogRepository,
                                 FeedbackNoteRepository feedbackNoteRepository,
                                 AdditionalNoteRepository additionalNoteRepository) {
        this.taskLogRepository = taskLogRepository;
        this.issueLogRepository = issueLogRepository;
        this.feedbackNoteRepository = feedbackNoteRepository;
        this.additionalNoteRepository = additionalNoteRepository;
    }

    public List<SearchResult> search(SearchRequest request) {
        return search(request.getQuery(), request.getSourceTypes());
    }

    public List<SearchResult> search(String query, List<SourceType> sourceTypes) {
        List<SourceType> types = (sourceTypes == null || sourceTypes.isEmpty())
                ? List.of(SourceType.TASK, SourceType.ISSUE, SourceType.FEEDBACK, SourceType.NOTE)
                : sourceTypes;
        String normalizedQuery = query == null ? "" : query.toLowerCase();

        boolean useEmbeddings = isApiKeyConfigured();
        float[] queryEmbedding = useEmbeddings ? createEmbedding(query) : null;
        if (queryEmbedding == null) {
            useEmbeddings = false;
        }

        List<SearchResult> results = new ArrayList<>();

        if (types.contains(SourceType.TASK)) {
            for (TaskLog t : taskLogRepository.findAll()) {
                String content = join(t.getTitle(), t.getDescription());
                addIfMatch(results, SourceType.TASK, t.getId(), content, normalizedQuery, queryEmbedding, useEmbeddings);
            }
        }
        if (types.contains(SourceType.ISSUE)) {
            for (IssueLog i : issueLogRepository.findAll()) {
                String content = join(i.getTitle(), i.getDescription(), i.getResolution());
                addIfMatch(results, SourceType.ISSUE, i.getId(), content, normalizedQuery, queryEmbedding, useEmbeddings);
            }
        }
        if (types.contains(SourceType.FEEDBACK)) {
            for (FeedbackNote f : feedbackNoteRepository.findAll()) {
                addIfMatch(results, SourceType.FEEDBACK, f.getId(), f.getContent(), normalizedQuery, queryEmbedding, useEmbeddings);
            }
        }
        if (types.contains(SourceType.NOTE)) {
            for (AdditionalNote n : additionalNoteRepository.findAll()) {
                String content = join(n.getTitle(), n.getContent());
                addIfMatch(results, SourceType.NOTE, n.getId(), content, normalizedQuery, queryEmbedding, useEmbeddings);
            }
        }

        results.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return results;
    }

    public void indexLog(SourceType sourceType, UUID sourceId, String content) {
        String key = key(sourceType, sourceId);
        contentStore.put(key, content == null ? "" : content);
        if (isApiKeyConfigured()) {
            float[] embedding = createEmbedding(content);
            if (embedding != null) {
                embeddingStore.put(key, embedding);
            }
        }
    }

    private void addIfMatch(List<SearchResult> results, SourceType type, UUID id, String content,
                            String normalizedQuery, float[] queryEmbedding, boolean useEmbeddings) {
        String safeContent = content == null ? "" : content;
        if (useEmbeddings && queryEmbedding != null) {
            float[] docEmbedding = embeddingStore.get(key(type, id));
            if (docEmbedding == null) {
                docEmbedding = createEmbedding(safeContent);
                if (docEmbedding != null) {
                    embeddingStore.put(key(type, id), docEmbedding);
                }
            }
            if (docEmbedding != null) {
                double score = cosineSimilarity(queryEmbedding, docEmbedding);
                results.add(SearchResult.builder()
                        .sourceType(type)
                        .sourceId(id)
                        .content(snippet(safeContent))
                        .score(score)
                        .build());
                return;
            }
        }
        if (normalizedQuery.isEmpty() || safeContent.toLowerCase().contains(normalizedQuery)) {
            results.add(SearchResult.builder()
                    .sourceType(type)
                    .sourceId(id)
                    .content(snippet(safeContent))
                    .score(textScore(safeContent.toLowerCase(), normalizedQuery))
                    .build());
        }
    }

    private double textScore(String content, String query) {
        if (query.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(query, idx)) != -1) {
            count++;
            idx += query.length();
        }
        return count;
    }

    private boolean isApiKeyConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    private float[] createEmbedding(String input) {
        if (!isApiKeyConfigured() || input == null || input.isBlank()) {
            return null;
        }
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);
            Map<String, Object> body = Map.of("model", embeddingModel, "input", input);
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.openai.com/v1/embeddings", entity, Map.class);
            if (response == null) {
                return null;
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return null;
            }
            List<Number> embedding = (List<Number>) data.get(0).get("embedding");
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 200 ? content : content.substring(0, 200) + "...";
    }

    private String key(SourceType type, UUID id) {
        return type.name() + ":" + id;
    }
}

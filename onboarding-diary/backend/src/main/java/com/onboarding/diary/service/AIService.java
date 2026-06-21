package com.onboarding.diary.service;

import com.onboarding.diary.entity.AdditionalNote;
import com.onboarding.diary.entity.FeedbackNote;
import com.onboarding.diary.entity.IssueLog;
import com.onboarding.diary.entity.TaskLog;
import com.onboarding.diary.repository.AdditionalNoteRepository;
import com.onboarding.diary.repository.FeedbackNoteRepository;
import com.onboarding.diary.repository.IssueLogRepository;
import com.onboarding.diary.repository.TaskLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AIService {

    private final TaskLogRepository taskLogRepository;
    private final IssueLogRepository issueLogRepository;
    private final FeedbackNoteRepository feedbackNoteRepository;
    private final AdditionalNoteRepository additionalNoteRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.model}")
    private String model;

    public AIService(TaskLogRepository taskLogRepository,
                     IssueLogRepository issueLogRepository,
                     FeedbackNoteRepository feedbackNoteRepository,
                     AdditionalNoteRepository additionalNoteRepository) {
        this.taskLogRepository = taskLogRepository;
        this.issueLogRepository = issueLogRepository;
        this.feedbackNoteRepository = feedbackNoteRepository;
        this.additionalNoteRepository = additionalNoteRepository;
    }

    public String generateWeeklySummary(UUID recruitId, int week) {
        List<TaskLog> tasks = taskLogRepository.findByUserId(recruitId);
        List<IssueLog> issues = issueLogRepository.findByUserId(recruitId);
        List<AdditionalNote> notes = additionalNoteRepository.findByUserId(recruitId);
        List<FeedbackNote> feedback = feedbackNoteRepository.findByRecruitId(recruitId).stream()
                .filter(f -> f.getWeek() != null && f.getWeek() == week)
                .toList();

        String prompt = buildPrompt(week, tasks, issues, feedback, notes);

        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return String.format(
                    "AI Summary (Mock): Week %d - The recruit had %d tasks, %d issues, %d feedback notes, and %d additional notes.",
                    week, tasks.size(), issues.size(), feedback.size(), notes.size());
        }

        try {
            return callOpenAi(prompt);
        } catch (Exception e) {
            return String.format(
                    "AI Summary (Mock): Week %d - The recruit had %d tasks, %d issues, %d feedback notes, and %d additional notes.",
                    week, tasks.size(), issues.size(), feedback.size(), notes.size());
        }
    }

    private String buildPrompt(int week, List<TaskLog> tasks, List<IssueLog> issues,
                               List<FeedbackNote> feedback, List<AdditionalNote> notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize this recruit's onboarding progress for week ").append(week).append(":\n");
        sb.append("\nTasks:\n");
        for (TaskLog t : tasks) {
            sb.append("- ").append(t.getTitle()).append(" [").append(t.getStatus()).append("]\n");
        }
        sb.append("\nIssues:\n");
        for (IssueLog i : issues) {
            sb.append("- ").append(i.getTitle()).append(" [").append(i.getStatus()).append("]\n");
        }
        sb.append("\nFeedback:\n");
        for (FeedbackNote f : feedback) {
            sb.append("- ").append(f.getContent()).append("\n");
        }
        sb.append("\nNotes:\n");
        for (AdditionalNote n : notes) {
            sb.append("- ").append(n.getTitle()).append(": ").append(n.getContent()).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String callOpenAi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an onboarding mentor summarizing recruit progress."),
                        Map.of("role", "user", "content", prompt)
                )
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(
                "https://api.openai.com/v1/chat/completions", entity, Map.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from OpenAI");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}

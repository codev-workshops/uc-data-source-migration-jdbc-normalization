package com.onboarding.diary.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.onboarding.diary.entity.AdditionalNote;
import com.onboarding.diary.entity.FeedbackNote;
import com.onboarding.diary.entity.IssueLog;
import com.onboarding.diary.entity.TaskLog;
import com.onboarding.diary.exception.BadRequestException;
import com.onboarding.diary.repository.AdditionalNoteRepository;
import com.onboarding.diary.repository.FeedbackNoteRepository;
import com.onboarding.diary.repository.IssueLogRepository;
import com.onboarding.diary.repository.TaskLogRepository;
import com.opencsv.CSVWriter;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private final TaskLogRepository taskLogRepository;
    private final IssueLogRepository issueLogRepository;
    private final FeedbackNoteRepository feedbackNoteRepository;
    private final AdditionalNoteRepository additionalNoteRepository;

    public ReportService(TaskLogRepository taskLogRepository,
                         IssueLogRepository issueLogRepository,
                         FeedbackNoteRepository feedbackNoteRepository,
                         AdditionalNoteRepository additionalNoteRepository) {
        this.taskLogRepository = taskLogRepository;
        this.issueLogRepository = issueLogRepository;
        this.feedbackNoteRepository = feedbackNoteRepository;
        this.additionalNoteRepository = additionalNoteRepository;
    }

    public byte[] generatePdf(UUID recruitId, LocalDate startDate, LocalDate endDate) {
        List<TaskLog> tasks = filterTasks(recruitId, startDate, endDate);
        List<IssueLog> issues = filterIssues(recruitId, startDate, endDate);
        List<FeedbackNote> feedback = filterFeedback(recruitId, startDate, endDate);
        List<AdditionalNote> notes = filterNotes(recruitId, startDate, endDate);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph title = new Paragraph("Onboarding Diary Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("Recruit ID: " + recruitId));
        document.add(new Paragraph("Range: " + startDate + " to " + endDate));
        document.add(new Paragraph(" "));

        addTaskSection(document, tasks);
        addIssueSection(document, issues);
        addFeedbackSection(document, feedback);
        addNoteSection(document, notes);

        document.close();
        return out.toByteArray();
    }

    public byte[] generateCsv(UUID recruitId, String type, LocalDate startDate, LocalDate endDate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            String normalized = type == null ? "tasks" : type.toLowerCase();
            switch (normalized) {
                case "tasks" -> writeTasksCsv(writer, filterTasks(recruitId, startDate, endDate));
                case "issues" -> writeIssuesCsv(writer, filterIssues(recruitId, startDate, endDate));
                case "feedback" -> writeFeedbackCsv(writer, filterFeedback(recruitId, startDate, endDate));
                case "notes" -> writeNotesCsv(writer, filterNotes(recruitId, startDate, endDate));
                default -> throw new BadRequestException("Unknown report type: " + type);
            }
            writer.flush();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to generate CSV: " + e.getMessage());
        }
        return out.toByteArray();
    }

    private void addTaskSection(Document document, List<TaskLog> tasks) {
        document.add(sectionTitle("Tasks"));
        PdfPTable table = new PdfPTable(new float[]{3, 2, 2, 2});
        table.setWidthPercentage(100);
        header(table, "Title", "Status", "Priority", "Due Date");
        for (TaskLog t : tasks) {
            table.addCell(safe(t.getTitle()));
            table.addCell(t.getStatus() == null ? "" : t.getStatus().name());
            table.addCell(t.getPriority() == null ? "" : t.getPriority().name());
            table.addCell(t.getDueDate() == null ? "" : t.getDueDate().toString());
        }
        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addIssueSection(Document document, List<IssueLog> issues) {
        document.add(sectionTitle("Issues"));
        PdfPTable table = new PdfPTable(new float[]{3, 2, 2, 3});
        table.setWidthPercentage(100);
        header(table, "Title", "Severity", "Status", "Resolution");
        for (IssueLog i : issues) {
            table.addCell(safe(i.getTitle()));
            table.addCell(i.getSeverity() == null ? "" : i.getSeverity().name());
            table.addCell(i.getStatus() == null ? "" : i.getStatus().name());
            table.addCell(safe(i.getResolution()));
        }
        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addFeedbackSection(Document document, List<FeedbackNote> feedback) {
        document.add(sectionTitle("Feedback"));
        PdfPTable table = new PdfPTable(new float[]{1, 4, 2});
        table.setWidthPercentage(100);
        header(table, "Week", "Content", "Manager");
        for (FeedbackNote f : feedback) {
            table.addCell(f.getWeek() == null ? "" : f.getWeek().toString());
            table.addCell(safe(f.getContent()));
            table.addCell(f.getManager() == null ? "" : safe(f.getManager().getFullName()));
        }
        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addNoteSection(Document document, List<AdditionalNote> notes) {
        document.add(sectionTitle("Notes"));
        PdfPTable table = new PdfPTable(new float[]{2, 4, 2});
        table.setWidthPercentage(100);
        header(table, "Title", "Content", "Category");
        for (AdditionalNote n : notes) {
            table.addCell(safe(n.getTitle()));
            table.addCell(safe(n.getContent()));
            table.addCell(safe(n.getCategory()));
        }
        document.add(table);
    }

    private void writeTasksCsv(CSVWriter writer, List<TaskLog> tasks) {
        writer.writeNext(new String[]{"Title", "Description", "Status", "Priority", "Due Date", "Created At"});
        for (TaskLog t : tasks) {
            writer.writeNext(new String[]{
                    safe(t.getTitle()), safe(t.getDescription()),
                    t.getStatus() == null ? "" : t.getStatus().name(),
                    t.getPriority() == null ? "" : t.getPriority().name(),
                    t.getDueDate() == null ? "" : t.getDueDate().toString(),
                    t.getCreatedAt() == null ? "" : t.getCreatedAt().toString()
            });
        }
    }

    private void writeIssuesCsv(CSVWriter writer, List<IssueLog> issues) {
        writer.writeNext(new String[]{"Title", "Description", "Severity", "Status", "Resolution", "Created At"});
        for (IssueLog i : issues) {
            writer.writeNext(new String[]{
                    safe(i.getTitle()), safe(i.getDescription()),
                    i.getSeverity() == null ? "" : i.getSeverity().name(),
                    i.getStatus() == null ? "" : i.getStatus().name(),
                    safe(i.getResolution()),
                    i.getCreatedAt() == null ? "" : i.getCreatedAt().toString()
            });
        }
    }

    private void writeFeedbackCsv(CSVWriter writer, List<FeedbackNote> feedback) {
        writer.writeNext(new String[]{"Week", "Content", "Manager", "Created At"});
        for (FeedbackNote f : feedback) {
            writer.writeNext(new String[]{
                    f.getWeek() == null ? "" : f.getWeek().toString(),
                    safe(f.getContent()),
                    f.getManager() == null ? "" : safe(f.getManager().getFullName()),
                    f.getCreatedAt() == null ? "" : f.getCreatedAt().toString()
            });
        }
    }

    private void writeNotesCsv(CSVWriter writer, List<AdditionalNote> notes) {
        writer.writeNext(new String[]{"Title", "Content", "Category", "Created At"});
        for (AdditionalNote n : notes) {
            writer.writeNext(new String[]{
                    safe(n.getTitle()), safe(n.getContent()), safe(n.getCategory()),
                    n.getCreatedAt() == null ? "" : n.getCreatedAt().toString()
            });
        }
    }

    private Paragraph sectionTitle(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        return new Paragraph(text, font);
    }

    private void header(PdfPTable table, String... headers) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            table.addCell(cell);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<TaskLog> filterTasks(UUID recruitId, LocalDate startDate, LocalDate endDate) {
        return taskLogRepository.findByUserId(recruitId).stream()
                .filter(t -> inRange(t.getCreatedAt(), startDate, endDate))
                .toList();
    }

    private List<IssueLog> filterIssues(UUID recruitId, LocalDate startDate, LocalDate endDate) {
        return issueLogRepository.findByUserId(recruitId).stream()
                .filter(i -> inRange(i.getCreatedAt(), startDate, endDate))
                .toList();
    }

    private List<FeedbackNote> filterFeedback(UUID recruitId, LocalDate startDate, LocalDate endDate) {
        return feedbackNoteRepository.findByRecruitId(recruitId).stream()
                .filter(f -> inRange(f.getCreatedAt(), startDate, endDate))
                .toList();
    }

    private List<AdditionalNote> filterNotes(UUID recruitId, LocalDate startDate, LocalDate endDate) {
        return additionalNoteRepository.findByUserId(recruitId).stream()
                .filter(n -> inRange(n.getCreatedAt(), startDate, endDate))
                .toList();
    }

    private boolean inRange(LocalDateTime timestamp, LocalDate startDate, LocalDate endDate) {
        if (timestamp == null) {
            return true;
        }
        if (startDate != null && timestamp.toLocalDate().isBefore(startDate)) {
            return false;
        }
        return endDate == null || !timestamp.toLocalDate().isAfter(endDate);
    }
}

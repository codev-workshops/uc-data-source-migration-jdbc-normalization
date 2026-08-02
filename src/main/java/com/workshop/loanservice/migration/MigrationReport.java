package com.workshop.loanservice.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of one migration run. Rejected rows are never dropped silently: each one is recorded with
 * the identifier and the reason, so a lenient run can be audited afterwards.
 */
public class MigrationReport {

    /** One rejected legacy row. */
    public record Rejection(String table, String legacyId, String reason) {
    }

    private final Map<String, Integer> read = new LinkedHashMap<>();
    private final Map<String, Integer> written = new LinkedHashMap<>();
    private final Map<String, Integer> skipped = new LinkedHashMap<>();
    private final List<Rejection> rejections = new ArrayList<>();
    private Duration duration = Duration.ZERO;

    public void addRead(String table, int count) {
        read.merge(table, count, Integer::sum);
    }

    public void addWritten(String table, int count) {
        written.merge(table, count, Integer::sum);
    }

    /** Rows that already existed in the modern store; the reason a re-run is a no-op. */
    public void addSkipped(String table, int count) {
        skipped.merge(table, count, Integer::sum);
    }

    public void addRejection(String table, String legacyId, String reason) {
        rejections.add(new Rejection(table, legacyId, reason));
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Map<String, Integer> getRead() {
        return read;
    }

    public Map<String, Integer> getWritten() {
        return written;
    }

    public Map<String, Integer> getSkipped() {
        return skipped;
    }

    public List<Rejection> getRejections() {
        return rejections;
    }

    public Duration getDuration() {
        return duration;
    }

    public int totalRead() {
        return read.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalWritten() {
        return written.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalSkipped() {
        return skipped.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasRejections() {
        return !rejections.isEmpty();
    }

    public String summary() {
        return "read=" + read + " written=" + written + " skipped=" + skipped
            + " rejected=" + rejections.size() + " durationMs=" + duration.toMillis();
    }
}

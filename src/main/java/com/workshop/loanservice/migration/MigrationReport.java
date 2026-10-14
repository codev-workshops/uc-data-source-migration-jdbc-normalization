package com.workshop.loanservice.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rows migrated per modern table, plus rejected rows and failed validation checks. */
public class MigrationReport {

    private final Map<String, Integer> migrated = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    void recordMigrated(String table) {
        migrated.merge(table, 1, Integer::sum);
    }

    void recordProblem(String problem) {
        problems.add(problem);
    }

    public int migrated(String table) {
        return migrated.getOrDefault(table, 0);
    }

    public List<String> getProblems() {
        return List.copyOf(problems);
    }

    public boolean isClean() {
        return problems.isEmpty();
    }

    @Override
    public String toString() {
        return "migrated " + migrated + ", problems " + problems;
    }
}

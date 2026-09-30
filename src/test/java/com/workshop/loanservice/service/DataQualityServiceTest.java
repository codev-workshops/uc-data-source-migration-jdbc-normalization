package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.DataQualityReport;
import com.workshop.loanservice.dto.RecordQualityScore;
import com.workshop.loanservice.validation.Severity;
import com.workshop.loanservice.validation.ValidationFinding;
import com.workshop.loanservice.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DataQualityServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scoreDeductsPerSeverityAndFloorsAtZero() {
        ValidationFinding error = finding(Severity.ERROR, "R-1");
        ValidationFinding warning = finding(Severity.WARNING, "R-1");

        assertThat(DataQualityService.score("T", "R-1", List.of()).score()).isEqualTo(100);
        assertThat(DataQualityService.score("T", "R-1", List.of(error, warning)))
                .isEqualTo(new RecordQualityScore("T", "R-1", 65, 1, 1));
        assertThat(DataQualityService.score("T", "R-1", Collections.nCopies(5, error)).score())
                .isZero();
    }

    @Test
    void reportAggregatesPerTableAndOverall() {
        Map<String, List<String>> ids = new LinkedHashMap<>();
        ids.put("T", List.of("R-1", "R-2"));
        ids.put("U", List.of("R-9"));
        DataQualityReport report = DataQualityService.buildReport(new ValidationResult(ids,
                List.of(finding(Severity.ERROR, "R-1"), finding(Severity.WARNING, "R-1"))));

        assertThat(report.totalRecords()).isEqualTo(3);
        assertThat(report.findingsBySeverity())
                .containsEntry(Severity.ERROR, 1L).containsEntry(Severity.WARNING, 1L);
        assertThat(report.minimumScore()).isEqualTo(65);
        assertThat(report.averageScore()).isEqualTo(88.33);
        assertThat(report.tables().get(0).averageScore()).isEqualTo(82.5);
        assertThat(report.tables().get(1).minimumScore()).isEqualTo(100);
    }

    @Test
    void reportEndpointServesSeededFindings() throws Exception {
        mockMvc.perform(get("/api/data-quality/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(25))
                .andExpect(jsonPath("$.findingsBySeverity.ERROR").value(0))
                .andExpect(jsonPath("$.findingsBySeverity.WARNING").value(9))
                .andExpect(jsonPath("$.minimumScore").value(80))
                .andExpect(jsonPath("$.tables", hasSize(4)))
                .andExpect(jsonPath("$.findings[?(@.recordId == 'LN-2018-00089')].column")
                        .value("LN_STAT_CD"));
    }

    private static ValidationFinding finding(Severity severity, String recordId) {
        return new ValidationFinding(severity, "T", recordId, "C", "msg");
    }
}

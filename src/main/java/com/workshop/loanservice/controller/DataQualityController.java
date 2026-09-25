package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.DataQualityReportDto;
import com.workshop.loanservice.service.DataQualityReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the legacy data-quality report as JSON and Markdown. */
@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

  private final DataQualityReportService reportService;

  public DataQualityController(DataQualityReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
  public DataQualityReportDto getReport() {
    return reportService.generateReport();
  }

  @GetMapping(value = "/report/markdown", produces = MediaType.TEXT_MARKDOWN_VALUE)
  public String getReportMarkdown() {
    return reportService.renderMarkdown(reportService.generateReport());
  }
}

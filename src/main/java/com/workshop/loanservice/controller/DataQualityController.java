package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.DataQualityReport;
import com.workshop.loanservice.service.DataQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping("/report")
    public DataQualityReport getReport() {
        return dataQualityService.generateReport();
    }
}

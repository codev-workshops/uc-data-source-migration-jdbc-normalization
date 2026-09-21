package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.MigrationService;
import com.workshop.loanservice.service.migration.MigrationSummary;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class MigrationController {

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /** Re-runs the legacy -> modern migration; idempotent, returns per-table counts and quarantined records. */
    @PostMapping("/migrate")
    public MigrationSummary migrate() {
        return migrationService.migrate();
    }
}

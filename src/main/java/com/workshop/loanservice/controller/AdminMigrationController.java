package com.workshop.loanservice.controller;

import com.workshop.loanservice.migration.DataMigrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin hook to re-run the legacy → modern migration. The migration is idempotent
 * (records already present under their legacy business key are skipped), so this can
 * safely be called after new legacy rows land.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMigrationController {

    private final DataMigrationService dataMigrationService;

    public AdminMigrationController(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    @PostMapping("/migrate")
    public DataMigrationService.MigrationReport migrate() {
        return dataMigrationService.migrateAll();
    }
}

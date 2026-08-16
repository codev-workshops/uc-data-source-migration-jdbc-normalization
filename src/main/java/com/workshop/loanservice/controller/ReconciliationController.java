package com.workshop.loanservice.controller;

import com.workshop.loanservice.reconciliation.ReconciliationReport;
import com.workshop.loanservice.reconciliation.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational endpoint for verifying a migration: reports whether the legacy and
 * modern data sources still agree. Not part of the public loan API.
 */
@RestController
@RequestMapping("/api/admin/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}

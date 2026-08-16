package com.workshop.loanservice.controller;

import com.workshop.loanservice.reconciliation.ReconciliationReport;
import com.workshop.loanservice.reconciliation.ReconciliationService;
import com.workshop.loanservice.reconciliation.SqlReconciliationService;
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
    private final SqlReconciliationService sqlReconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService,
                                    SqlReconciliationService sqlReconciliationService) {
        this.reconciliationService = reconciliationService;
        this.sqlReconciliationService = sqlReconciliationService;
    }

    @GetMapping
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }

    /** Table-level comparison from {@code validation/reconciliation_queries.sql}. */
    @GetMapping("/sql")
    public ReconciliationReport reconcileWithSql() {
        return sqlReconciliationService.reconcile();
    }
}

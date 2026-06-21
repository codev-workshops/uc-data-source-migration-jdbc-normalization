package com.onboarding.diary.controller;

import com.onboarding.diary.dto.DashboardSummary;
import com.onboarding.diary.dto.WeeklyStat;
import com.onboarding.diary.security.UserPrincipal;
import com.onboarding.diary.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getSummary(principal.getUser()));
    }

    @GetMapping("/weekly-stats")
    public ResponseEntity<List<WeeklyStat>> weeklyStats(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getWeeklyStats(principal.getUser()));
    }
}

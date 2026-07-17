package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.DataSourceSelector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Runtime control for the dual-read feature flag: inspect and switch the active
 * data source ({@code legacy} / {@code modern}) without a restart.
 */
@RestController
@RequestMapping("/api/admin/datasource")
public class DataSourceAdminController {

    private final DataSourceSelector selector;

    public DataSourceAdminController(DataSourceSelector selector) {
        this.selector = selector;
    }

    @GetMapping
    public Map<String, String> current() {
        return Map.of("active", selector.getActive().name().toLowerCase());
    }

    @PutMapping("/{dataSource}")
    public ResponseEntity<Map<String, String>> switchTo(@PathVariable String dataSource) {
        try {
            DataSourceSelector.DataSource active = selector.setActive(dataSource);
            return ResponseEntity.ok(Map.of("active", active.name().toLowerCase()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown data source: " + dataSource));
        }
    }
}

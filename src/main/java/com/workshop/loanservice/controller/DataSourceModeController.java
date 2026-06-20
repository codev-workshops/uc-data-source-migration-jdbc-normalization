package com.workshop.loanservice.controller;

import com.workshop.loanservice.config.DataSourceMode;
import com.workshop.loanservice.config.DataSourceModeHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoint backing the dual-read feature flag. Lets operators inspect and
 * flip the active data source ({@code MODERN} <-> {@code LEGACY}) at runtime,
 * without a restart, to support a controlled, reversible cutover.
 *
 * Lives under /api/admin so it does not affect the public loan/borrower contract.
 */
@RestController
@RequestMapping("/api/admin/datasource-mode")
public class DataSourceModeController {

    private final DataSourceModeHolder modeHolder;

    public DataSourceModeController(DataSourceModeHolder modeHolder) {
        this.modeHolder = modeHolder;
    }

    @GetMapping
    public Map<String, DataSourceMode> getMode() {
        return Map.of("mode", modeHolder.getMode());
    }

    @PutMapping("/{mode}")
    public Map<String, DataSourceMode> setMode(@PathVariable String mode) {
        DataSourceMode requested = DataSourceMode.valueOf(mode.trim().toUpperCase());
        DataSourceMode previous = modeHolder.getMode();
        modeHolder.setMode(requested);
        return Map.of("previous", previous, "mode", requested);
    }
}

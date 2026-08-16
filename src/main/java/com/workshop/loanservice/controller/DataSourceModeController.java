package com.workshop.loanservice.controller;

import com.workshop.loanservice.provider.DataSourceModeSelector;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational endpoint for reading and switching the data source that serves
 * loan reads, without restarting the application. Not part of the public loan API.
 */
@RestController
@RequestMapping("/api/admin/datasource-mode")
public class DataSourceModeController {

    private final DataSourceModeSelector selector;

    public DataSourceModeController(DataSourceModeSelector selector) {
        this.selector = selector;
    }

    @GetMapping
    public Map<String, Object> current() {
        return state(null);
    }

    /** Body: {@code {"mode": "legacy"}}. Switching to the active mode is a no-op. */
    @PutMapping
    public Map<String, Object> switchTo(@RequestBody ModeRequest request) {
        try {
            return state(selector.switchTo(request.mode()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private Map<String, Object> state(String previousMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", selector.activeMode());
        if (previousMode != null) {
            body.put("previousMode", previousMode);
        }
        body.put("availableModes", selector.availableModes());
        return body;
    }

    public record ModeRequest(String mode) {
    }
}

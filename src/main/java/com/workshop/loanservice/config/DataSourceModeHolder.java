package com.workshop.loanservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the active {@link DataSourceMode} for the dual-read feature flag.
 *
 * Initialized from the {@code loanservice.datasource.mode} property and can be
 * flipped at runtime (thread-safe via a volatile field) without a restart.
 */
@Component
public class DataSourceModeHolder {

    private volatile DataSourceMode mode;

    public DataSourceModeHolder(
            @Value("${loanservice.datasource.mode:MODERN}") DataSourceMode initialMode) {
        this.mode = initialMode;
    }

    public DataSourceMode getMode() {
        return mode;
    }

    public void setMode(DataSourceMode mode) {
        this.mode = mode;
    }
}

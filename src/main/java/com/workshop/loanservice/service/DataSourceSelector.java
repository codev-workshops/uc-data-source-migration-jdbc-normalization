package com.workshop.loanservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Dual-read feature flag: holds the currently active data source and can be
 * flipped at runtime (see {@code DataSourceAdminController}). Defaults to the
 * {@code loanservice.datasource} property.
 */
@Component
public class DataSourceSelector {

    public enum DataSource {
        LEGACY,
        MODERN;

        static DataSource parse(String value) {
            return DataSource.valueOf(value.trim().toUpperCase());
        }
    }

    private final AtomicReference<DataSource> active;

    public DataSourceSelector(@Value("${loanservice.datasource:legacy}") String initial) {
        this.active = new AtomicReference<>(DataSource.parse(initial));
    }

    public DataSource getActive() {
        return active.get();
    }

    public DataSource setActive(DataSource dataSource) {
        active.set(dataSource);
        return dataSource;
    }

    public DataSource setActive(String dataSource) {
        return setActive(DataSource.parse(dataSource));
    }
}

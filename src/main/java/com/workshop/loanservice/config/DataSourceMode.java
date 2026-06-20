package com.workshop.loanservice.config;

/** Selects which persistence model the read path serves from. */
public enum DataSourceMode {
    LEGACY,
    MODERN
}

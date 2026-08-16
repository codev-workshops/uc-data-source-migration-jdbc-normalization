package com.workshop.loanservice.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds which {@link LoanDataProvider} serves reads, and lets it be switched at
 * runtime without a restart.
 *
 * <p>{@code loanservice.datasource.mode} supplies the initial value only. The
 * active provider is read per request through an {@link AtomicReference}, so a
 * switch takes effect on the next read and never leaves a request half-served by
 * two data sources.
 */
@Component
public class DataSourceModeSelector {

    private static final Logger log = LoggerFactory.getLogger(DataSourceModeSelector.class);

    private final Map<String, LoanDataProvider> providersByName;
    private final AtomicReference<LoanDataProvider> active = new AtomicReference<>();

    public DataSourceModeSelector(List<LoanDataProvider> providers,
                                  @Value("${loanservice.datasource.mode:modern}") String initialMode) {
        this.providersByName = providers.stream().collect(Collectors.toMap(
                provider -> provider.name().toLowerCase(Locale.ROOT), Function.identity()));
        this.active.set(require(initialMode));
        log.info("Loan reads served from the {} data source", active.get().name());
    }

    public LoanDataProvider active() {
        return active.get();
    }

    public String activeMode() {
        return active.get().name();
    }

    /** Available modes, so callers can discover them instead of guessing. */
    public List<String> availableModes() {
        return providersByName.values().stream().map(LoanDataProvider::name).sorted().toList();
    }

    /**
     * Switches the data source serving reads and returns the mode replaced.
     *
     * @throws IllegalArgumentException if no provider answers to {@code mode}
     */
    public String switchTo(String mode) {
        LoanDataProvider previous = active.getAndSet(require(mode));
        log.info("Loan reads switched from the {} to the {} data source",
                previous.name(), active.get().name());
        return previous.name();
    }

    private LoanDataProvider require(String mode) {
        LoanDataProvider provider = mode == null
                ? null
                : providersByName.get(mode.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown data source mode '" + mode + "'; expected one of " + availableModes());
        }
        return provider;
    }
}

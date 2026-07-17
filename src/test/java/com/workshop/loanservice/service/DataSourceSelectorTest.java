package com.workshop.loanservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link DataSourceSelector}, the dual-read feature flag: default
 * initialization from the configured property (case-insensitive), runtime
 * switching by enum and by string, and rejection of unknown values.
 */
class DataSourceSelectorTest {

    @Test
    void defaultsToConfiguredValueAndIsCaseInsensitive() {
        assertEquals(DataSourceSelector.DataSource.LEGACY,
                new DataSourceSelector("legacy").getActive());
        assertEquals(DataSourceSelector.DataSource.MODERN,
                new DataSourceSelector("  Modern ").getActive());
    }

    @Test
    void setActiveByEnumReturnsAndStoresValue() {
        DataSourceSelector selector = new DataSourceSelector("legacy");
        assertEquals(DataSourceSelector.DataSource.MODERN,
                selector.setActive(DataSourceSelector.DataSource.MODERN));
        assertEquals(DataSourceSelector.DataSource.MODERN, selector.getActive());
    }

    @Test
    void setActiveByStringParsesValue() {
        DataSourceSelector selector = new DataSourceSelector("modern");
        assertEquals(DataSourceSelector.DataSource.LEGACY, selector.setActive("LEGACY"));
        assertEquals(DataSourceSelector.DataSource.LEGACY, selector.getActive());
    }

    @Test
    void unknownValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DataSourceSelector("bogus"));
        DataSourceSelector selector = new DataSourceSelector("legacy");
        assertThrows(IllegalArgumentException.class, () -> selector.setActive("nope"));
    }
}

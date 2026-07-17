package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.DataSourceSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link DataSourceAdminController}: reading the active data
 * source, switching it at runtime, and returning {@code 400} for an unknown
 * data source without changing the active selection.
 */
class DataSourceAdminControllerTest {

    private DataSourceSelector selector;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        selector = new DataSourceSelector("legacy");
        mockMvc = MockMvcBuilders.standaloneSetup(new DataSourceAdminController(selector)).build();
    }

    @Test
    void reportsCurrentDataSource() throws Exception {
        mockMvc.perform(get("/api/admin/datasource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("legacy"));
    }

    @Test
    void switchesToModern() throws Exception {
        mockMvc.perform(put("/api/admin/datasource/modern"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("modern"));
        assertEquals(DataSourceSelector.DataSource.MODERN, selector.getActive());
    }

    @Test
    void rejectsUnknownDataSource() throws Exception {
        mockMvc.perform(put("/api/admin/datasource/bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown data source: bogus"));
        // active source is unchanged after a bad request
        assertEquals(DataSourceSelector.DataSource.LEGACY, selector.getActive());
    }

    @Test
    void switchIsCaseInsensitive() throws Exception {
        mockMvc.perform(put("/api/admin/datasource/MODERN"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("modern")));
    }
}

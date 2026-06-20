package com.workshop.loanservice;

import com.workshop.loanservice.config.DataSourceMode;
import com.workshop.loanservice.config.DataSourceModeHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the dual-read feature flag: the modern and legacy read paths must
 * return byte-identical JSON for every public endpoint, and the active mode must
 * be switchable at runtime via the admin endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DualReadModeTest {

    @Autowired MockMvc mockMvc;
    @Autowired DataSourceModeHolder modeHolder;

    private static final String[] ENDPOINTS = {
            "/api/loans",
            "/api/loans/LN-2017-00034",
            "/api/borrowers",
            "/api/borrowers/B-10001",
            "/api/loans/LN-2019-00142/payments"
    };

    @AfterEach
    void resetMode() {
        modeHolder.setMode(DataSourceMode.MODERN);
    }

    @Test
    void modernAndLegacyReadPathsAreByteIdentical() throws Exception {
        for (String endpoint : ENDPOINTS) {
            modeHolder.setMode(DataSourceMode.MODERN);
            String modern = mockMvc.perform(get(endpoint))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            modeHolder.setMode(DataSourceMode.LEGACY);
            String legacy = mockMvc.perform(get(endpoint))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(modern)
                    .as("dual-read parity for %s", endpoint)
                    .isEqualTo(legacy);
        }
    }

    @Test
    void modeIsReportedAndSwitchableAtRuntime() throws Exception {
        mockMvc.perform(get("/api/admin/datasource-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MODERN"));

        mockMvc.perform(put("/api/admin/datasource-mode/LEGACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous").value("MODERN"))
                .andExpect(jsonPath("$.mode").value("LEGACY"));

        assertThat(modeHolder.getMode()).isEqualTo(DataSourceMode.LEGACY);

        mockMvc.perform(get("/api/admin/datasource-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LEGACY"));
    }
}

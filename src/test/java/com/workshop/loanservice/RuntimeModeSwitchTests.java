package com.workshop.loanservice;

import com.workshop.loanservice.provider.DataSourceModeSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The read data source can be switched while the application is running. */
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeModeSwitchTests {

    private static final String ENDPOINT = "/api/admin/datasource-mode";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSourceModeSelector selector;

    @AfterEach
    void restoreDefaultMode() {
        selector.switchTo("modern");
    }

    @Test
    void reportsTheActiveAndAvailableModes() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("modern"))
                .andExpect(jsonPath("$.availableModes[0]").value("legacy"))
                .andExpect(jsonPath("$.availableModes[1]").value("modern"));
    }

    @Test
    void switchesToLegacyAndBackWithoutRestarting() throws Exception {
        String modernLoans = body(get("/api/loans"));

        mockMvc.perform(switchMode("legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("legacy"))
                .andExpect(jsonPath("$.previousMode").value("modern"));
        assertThat(selector.activeMode()).isEqualTo("legacy");

        // The point of the switch: the same bytes, served from the other database.
        assertThat(body(get("/api/loans"))).isEqualTo(modernLoans);

        mockMvc.perform(switchMode("modern"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("modern"))
                .andExpect(jsonPath("$.previousMode").value("legacy"));
        assertThat(body(get("/api/loans"))).isEqualTo(modernLoans);
    }

    @Test
    void rejectsAnUnknownModeAndKeepsServingTheCurrentOne() throws Exception {
        mockMvc.perform(switchMode("postgres")).andExpect(status().isBadRequest());

        assertThat(selector.activeMode()).isEqualTo("modern");
        mockMvc.perform(get("/api/loans")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder switchMode(String mode) {
        return put(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"" + mode + "\"}");
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }
}

package com.workshop.loanservice;

import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The legacy read path stays available behind {@code loanservice.datasource.mode=legacy}. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "loanservice.datasource.mode=legacy")
class LegacyModeApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private LoanService loanService;

    @Test
    void legacyModeStillServesTheSameResponses() throws Exception {
        assertThat(loanService.activeDataSourceMode()).isEqualTo("legacy");

        String actual = mockMvc.perform(get("/api/loans"))
                .andReturn().getResponse().getContentAsString();

        try (var in = new ClassPathResource("golden/loans.json").getInputStream()) {
            assertThat(actual).isEqualTo(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }
}

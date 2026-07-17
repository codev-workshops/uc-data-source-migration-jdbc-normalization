package com.workshop.loanservice.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.loanservice.service.LoanService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A live, per-profile Spring context wired for one data source, together with the
 * handles the contract assertions need. Keeping these together is what lets the
 * assertion code stay datasource-agnostic: it just asks for a {@code DataSourceContext}
 * by name and runs the identical golden-file checks against it.
 */
public record DataSourceContext(
        String dataSource,
        ConfigurableApplicationContext context,
        MockMvc mockMvc,
        ObjectMapper objectMapper) {

    /** Service bean from this data source's context (for service-level assertions). */
    public LoanService loanService() {
        return context.getBean(LoanService.class);
    }
}

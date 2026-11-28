package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.service.ResourceNotFoundException;
import com.workshop.loanservice.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {LoanController.class, BorrowerController.class})
@Import({ApiExceptionHandler.class, RequestIdFilter.class, ApiExceptionHandlerTest.ProbeController.class})
class ApiExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/number")
        int number(@RequestParam int value) {
            return value;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanService loanService;

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        when(loanService.getAllLoans()).thenThrow(new IllegalStateException("jdbc:h2:mem:loandb password=hunter2"));

        mockMvc.perform(get("/api/loans").header(RequestIdFilter.HEADER, "trace-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-500"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected error; quote the requestId when reporting it"))
                .andExpect(jsonPath("$.path").value("/api/loans"))
                .andExpect(jsonPath("$.requestId").value("trace-500"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void legacyDataParseErrorIs500WithoutInternalDetails() throws Exception {
        when(loanService.getBorrowerById("B-1")).thenThrow(new NumberFormatException("For input string: \"12a\""));

        mockMvc.perform(get("/api/borrowers/B-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("12a"))));
    }

    @Test
    void notFoundCarriesTheRequestId() throws Exception {
        when(loanService.getLoanById("1")).thenThrow(new ResourceNotFoundException("Loan", "1"));

        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andExpect(jsonPath("$.message").value("Loan not found: 1"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownRouteIs404Json() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void typeMismatchIs400() throws Exception {
        mockMvc.perform(get("/probe/number").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'value'"));
    }

    @Test
    void missingParameterIs400() throws Exception {
        mockMvc.perform(get("/probe/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("value")));
    }

    @Test
    void unsupportedMethodIs405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/loans"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }
}

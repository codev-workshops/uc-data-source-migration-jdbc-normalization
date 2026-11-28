package com.workshop.loanservice.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesIdPutsItInMdcDuringTheRequestAndClearsItAfter() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/loans"), response,
                (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(seenInChain.get()).isEqualTo(id);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void echoesSafeCallerId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/loans");
        request.addHeader(RequestIdFilter.HEADER, "client-123.abc:9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-123.abc:9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "line\nbreak", "x\r\nFAKE LOG LINE",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void replacesUnsafeCallerIdWithUuid(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/loans");
        request.addHeader(RequestIdFilter.HEADER, unsafe);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
    }
}

package org.platform.commons.correlation;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final CorrelationIdFilter filter = new CorrelationIdFilter(HEADER, MDC_KEY);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures what the MDC held while the request was being handled. */
    private AtomicReference<String> runFilter(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(MDC_KEY));
        filter.doFilter(request, response, chain);
        return seen;
    }

    @Test
    void generatesAnIdWhenTheCallerSuppliesNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runFilter(new MockHttpServletRequest(), response).get();

        assertThat(seen).isNotBlank();
        assertThat(UUID.fromString(seen)).isNotNull();
        assertThat(response.getHeader(HEADER)).isEqualTo(seen);
    }

    @Test
    void reusesTheIdSuppliedByTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "upstream-request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runFilter(request, response).get();

        assertThat(seen).isEqualTo("upstream-request-42");
        assertThat(response.getHeader(HEADER)).isEqualTo("upstream-request-42");
    }

    @Test
    void stripsCharactersThatCouldForgeALogLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "abc\n WARN fake-log-entry");

        String seen = runFilter(request, new MockHttpServletResponse()).get();

        assertThat(seen).isEqualTo("abcWARNfake-log-entry");
    }

    @Test
    void truncatesAnOverlongId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "x".repeat(500));

        String seen = runFilter(request, new MockHttpServletResponse()).get();

        assertThat(seen).hasSize(64);
    }

    @Test
    void generatesAnIdWhenTheSuppliedOneSanitizesToNothing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "!!!");

        String seen = runFilter(request, new MockHttpServletResponse()).get();

        assertThat(UUID.fromString(seen)).isNotNull();
    }

    @Test
    void clearsTheMdcSoPooledThreadsDoNotLeakIds() throws Exception {
        runFilter(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheRequestFails() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("handler blew up");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // the filter must not swallow it
        }

        assertThat(MDC.get(MDC_KEY)).isNull();
    }
}

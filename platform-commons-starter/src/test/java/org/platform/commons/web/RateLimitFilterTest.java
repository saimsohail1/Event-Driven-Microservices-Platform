package org.platform.commons.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void letsTrafficThroughWhileTheBudgetLasts() throws Exception {
        RateLimitFilter filter = filter(2, Duration.ofMinutes(1));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        assertThat(statusOf(filter, request("10.0.0.1"), chain)).isEqualTo(200);
        assertThat(statusOf(filter, request("10.0.0.1"), chain)).isEqualTo(200);
        assertThat(passed).hasValue(2);
    }

    @Test
    void rejectsTheCallThatExhaustsTheBudget() throws Exception {
        RateLimitFilter filter = filter(1, Duration.ofMinutes(1));
        FilterChain chain = (req, res) -> {
        };

        statusOf(filter, request("10.0.0.2"), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        MDC.put("correlationId", "rate-1");
        try {
            filter.doFilter(request("10.0.0.2"), rejected, chain);
        } finally {
            MDC.clear();
        }

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader(RateLimitFilter.RETRY_AFTER)).isNotBlank();
        ApiError body = mapper.readValue(rejected.getContentAsByteArray(), ApiError.class);
        assertThat(body.status()).isEqualTo(429);
        assertThat(body.message()).isEqualTo("Rate limit exceeded");
        assertThat(body.correlationId()).isEqualTo("rate-1");
        assertThat(body.path()).isEqualTo("/api/orders");
    }

    @Test
    void tracksCallersSeparately() throws Exception {
        RateLimitFilter filter = filter(1, Duration.ofMinutes(1));
        FilterChain chain = (req, res) -> {
        };

        assertThat(statusOf(filter, request("10.0.0.3"), chain)).isEqualTo(200);
        assertThat(statusOf(filter, request("10.0.0.4"), chain)).isEqualTo(200);
    }

    @Test
    void refillsAfterTheWindow() throws Exception {
        RateLimitFilter filter = filter(1, Duration.ofMillis(40));
        FilterChain chain = (req, res) -> {
        };

        assertThat(statusOf(filter, request("10.0.0.5"), chain)).isEqualTo(200);
        assertThat(statusOf(filter, request("10.0.0.5"), chain)).isEqualTo(429);
        Thread.sleep(50);
        assertThat(statusOf(filter, request("10.0.0.5"), chain)).isEqualTo(200);
    }

    @Test
    void doesNotSpendBudgetOnActuator() throws Exception {
        RateLimitFilter filter = filter(1, Duration.ofMinutes(1));
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletRequest probe = new MockHttpServletRequest("GET", "/actuator/health");
        probe.setRemoteAddr("10.0.0.6");
        assertThat(filter.shouldNotFilter(probe)).isTrue();
        assertThat(statusOf(filter, request("10.0.0.6"), chain)).isEqualTo(200);
    }

    private RateLimitFilter filter(int requests, Duration window) {
        PlatformProperties.RateLimit properties = new PlatformProperties.RateLimit();
        properties.setRequests(requests);
        properties.setWindow(window);
        return new RateLimitFilter(properties, "correlationId", mapper);
    }

    private static MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setRemoteAddr(ip);
        return request;
    }

    private static int statusOf(RateLimitFilter filter, MockHttpServletRequest request, FilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }
}

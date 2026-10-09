package org.platform.commons.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.platform.commons.PlatformProperties;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps inbound HTTP traffic per client. A burst that would take the service
 * down is rejected with 429 rather than queued, so one noisy caller cannot
 * starve the others.
 */
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    static final String OVERFLOW_KEY = "_overflow";
    static final String RETRY_AFTER = "Retry-After";

    private final PlatformProperties.RateLimit properties;
    private final String mdcKey;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(PlatformProperties.RateLimit properties, String mdcKey, ObjectMapper objectMapper) {
        this.properties = properties;
        this.mdcKey = mdcKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Probes and scrapes must not compete with user traffic for the budget.
        return path != null && path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = clientKey(request);
        long now = System.nanoTime();
        long windowNanos = properties.getWindow().toNanos();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));

        int count;
        long retryAfterSeconds;
        synchronized (window) {
            if (now - window.startedAtNanos >= windowNanos) {
                window.startedAtNanos = now;
                window.count.set(0);
            }
            count = window.count.incrementAndGet();
            long remainingNanos = Math.max(0, windowNanos - (now - window.startedAtNanos));
            retryAfterSeconds = Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
        }

        if (count > properties.getRequests()) {
            reject(request, response, retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        if (!windows.containsKey(ip) && windows.size() >= properties.getMaxKeys()) {
            return OVERFLOW_KEY;
        }
        return ip;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded",
                request.getRequestURI(),
                MDC.get(mdcKey));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Override
    public int getOrder() {
        // After the correlation filter so a 429 still carries an id.
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static final class Window {
        volatile long startedAtNanos;
        final AtomicInteger count = new AtomicInteger();

        Window(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }
}

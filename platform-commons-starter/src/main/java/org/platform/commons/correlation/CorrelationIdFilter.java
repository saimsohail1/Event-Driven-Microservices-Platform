package org.platform.commons.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id: reuses the inbound header when the
 * caller supplied one, generates one otherwise, and publishes it to the MDC so
 * every log line for the request carries it.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    private static final int MAX_LENGTH = 64;

    private final String headerName;
    private final String mdcKey;

    public CorrelationIdFilter(String headerName, String mdcKey) {
        this.headerName = headerName;
        this.mdcKey = mdcKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        MDC.put(mdcKey, correlationId);
        // Echo it back so the caller can quote it when reporting a problem.
        response.setHeader(headerName, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Request threads are pooled, so a leaked MDC entry would be
            // attributed to whichever request runs next on this thread.
            MDC.remove(mdcKey);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String sanitized = sanitize(request.getHeader(headerName));
        return sanitized.isEmpty() ? UUID.randomUUID().toString() : sanitized;
    }

    /**
     * The inbound value is caller-controlled and ends up in log output, so
     * anything that could forge a log line or bloat it is dropped.
     */
    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        for (int i = 0; i < value.length() && cleaned.length() < MAX_LENGTH; i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    @Override
    public int getOrder() {
        // Ahead of application filters, so anything they log is correlated.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

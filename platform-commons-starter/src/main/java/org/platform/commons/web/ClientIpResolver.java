package org.platform.commons.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Picks the caller the rate limiter should charge. Prefers the first
 * {@code X-Forwarded-For} hop because nginx sits in front of every service
 * and sets that header; falls back to the socket address.
 */
public final class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";
    static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            String sanitized = sanitize(first);
            if (!sanitized.isEmpty()) {
                return sanitized;
            }
        }
        String remote = request.getRemoteAddr();
        String sanitized = sanitize(remote);
        return sanitized.isEmpty() ? UNKNOWN : sanitized;
    }

    /**
     * The value is caller-controlled, so anything that is not an address
     * character is dropped and the result is capped.
     */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), 64));
        for (int i = 0; i < value.length() && cleaned.length() < 64; i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == ':' || c == '-') {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }
}

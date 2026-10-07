package org.platform.commons.web;

import java.time.Instant;

/**
 * The single error shape every platform service returns.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId) {
}

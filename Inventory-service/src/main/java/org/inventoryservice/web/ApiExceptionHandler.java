package org.inventoryservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.inventoryservice.exception.UnknownProductException;
import org.platform.commons.web.ApiError;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Domain exceptions only. Framework-level failures (validation, 500s) are
 * handled by the starter's {@code PlatformExceptionHandler}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownProductException.class)
    public ResponseEntity<ApiError> onUnknownProduct(UnknownProductException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(Instant.now(), 404, "Not Found", e.getMessage(),
                        request.getRequestURI(), MDC.get("correlationId")));
    }
}

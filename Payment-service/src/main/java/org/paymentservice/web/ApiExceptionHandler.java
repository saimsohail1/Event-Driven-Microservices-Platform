package org.paymentservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.paymentservice.exception.PaymentAlreadyExistsException;
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

    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<ApiError> onDuplicatePayment(PaymentAlreadyExistsException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(Instant.now(), 409, "Conflict", e.getMessage(),
                        request.getRequestURI(), MDC.get("correlationId")));
    }
}

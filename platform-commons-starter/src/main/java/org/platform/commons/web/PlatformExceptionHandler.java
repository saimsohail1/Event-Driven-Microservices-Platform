package org.platform.commons.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates the framework-level failures every service shares into one error
 * shape.
 *
 * <p>Ordered last on purpose. A service's own advice stays more specific and
 * keeps winning for its domain exceptions; this one is the fallback.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformExceptionHandler.class);

    private final String mdcKey;

    public PlatformExceptionHandler(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, blankToDefault(message, "Invalid request"), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onInvalidParameter(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, blankToDefault(message, "Invalid request"), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onMalformedBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> onBadParameters(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters", request);
    }

    /**
     * Lets a service signal a status without writing its own advice.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> onResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        String reason = e.getReason() == null ? e.getStatusCode().toString() : e.getReason();
        return problem(e.getStatusCode(), reason, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e, HttpServletRequest request) {
        // The message is deliberately generic; the detail belongs in the log,
        // correlated by id, not in the response.
        log.error("Unhandled error serving {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ApiError> problem(HttpStatusCode status, String message, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                resolved == null ? "Error" : resolved.getReasonPhrase(),
                message,
                request.getRequestURI(),
                MDC.get(mdcKey));
        return ResponseEntity.status(status).body(body);
    }

    private String blankToDefault(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}

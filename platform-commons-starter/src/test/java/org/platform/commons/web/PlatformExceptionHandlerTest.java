package org.platform.commons.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformExceptionHandlerTest {

    private static final String MDC_KEY = "correlationId";

    private final PlatformExceptionHandler handler = new PlatformExceptionHandler(MDC_KEY);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setRequestURI("/api/orders");
        return request;
    }

    @Test
    void unexpectedFailuresReturnTheSharedShapeWithoutLeakingDetail() {
        MDC.put(MDC_KEY, "request-7");

        ResponseEntity<ApiError> response =
                handler.onUnexpected(new IllegalStateException("connection pool exhausted at 10.0.0.4"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Internal Server Error");
        assertThat(body.path()).isEqualTo("/api/orders");
        assertThat(body.correlationId()).isEqualTo("request-7");
        // Internal detail belongs in the correlated log line, not the response.
        assertThat(body.message()).isEqualTo("Internal server error");
    }

    @Test
    void correlationIdIsNullWhenNoneWasEstablished() {
        ResponseEntity<ApiError> response = handler.onUnexpected(new RuntimeException("boom"), request());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().correlationId()).isNull();
    }

    @Test
    void validationFailuresReportEveryFieldMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createOrderRequest");
        bindingResult.addError(new FieldError("createOrderRequest", "productId", "productId is required"));
        bindingResult.addError(new FieldError("createOrderRequest", "quantity", "quantity must be greater than zero"));

        Method method = Dummy.class.getDeclaredMethod("handle", String.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiError> response = handler.onInvalidBody(exception, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("productId is required; quantity must be greater than zero");
    }

    @Test
    void malformedBodiesAreRejectedAsBadRequests() {
        HttpInputMessage body = new MockHttpInputMessage(new byte[0]);
        ResponseEntity<ApiError> response =
                handler.onMalformedBody(new HttpMessageNotReadableException("nope", body), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    void responseStatusExceptionsKeepTheirStatusAndReason() {
        ResponseEntity<ApiError> response = handler.onResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Payment already exists"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).isEqualTo("Payment already exists");
    }

    @SuppressWarnings("unused")
    private static final class Dummy {
        void handle(String body) {
        }
    }
}

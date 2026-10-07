package org.platform.commons.client;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class RetryingClientHttpRequestInterceptorTest {

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void retriesServerErrorsUntilOneSucceeds() throws IOException {
        ClientHttpRequestExecution execution = responses(
                HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.OK);

        ClientHttpResponse response = interceptor(3, false)
                .intercept(request(HttpMethod.GET), new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void returnsTheLastResponseOnceAttemptsAreExhausted() throws IOException {
        ClientHttpRequestExecution execution = responses(
                HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY, HttpStatus.OK);

        ClientHttpResponse response = interceptor(3, false)
                .intercept(request(HttpMethod.GET), new byte[0], execution);

        // The caller sees the real failure rather than a retry exception, so
        // normal RestClient error handling still applies.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryClientErrors() throws IOException {
        ClientHttpRequestExecution execution = responses(HttpStatus.NOT_FOUND, HttpStatus.OK);

        ClientHttpResponse response = interceptor(3, false)
                .intercept(request(HttpMethod.GET), new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesTransportFailures() throws IOException {
        ClientHttpRequestExecution execution = (request, body) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("connection reset");
            }
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        };

        ClientHttpResponse response = interceptor(3, false)
                .intercept(request(HttpMethod.GET), new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void rethrowsTheTransportFailureOnceAttemptsAreExhausted() {
        ClientHttpRequestExecution execution = (request, body) -> {
            attempts.incrementAndGet();
            throw new IOException("connection reset");
        };

        assertThatIOException()
                .isThrownBy(() -> interceptor(2, false).intercept(request(HttpMethod.GET), new byte[0], execution))
                .withMessage("connection reset");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void leavesPostAloneByDefault() throws IOException {
        ClientHttpRequestExecution execution = responses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.OK);

        ClientHttpResponse response = interceptor(3, false)
                .intercept(request(HttpMethod.POST), new byte[0], execution);

        // Retrying a POST that timed out could charge a card twice.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesPostWhenTheServiceOptsIn() throws IOException {
        ClientHttpRequestExecution execution = responses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.OK);

        ClientHttpResponse response = interceptor(3, true)
                .intercept(request(HttpMethod.POST), new byte[0], execution);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void closesTheResponsesItAbandons() throws IOException {
        Deque<TrackedResponse> issued = new ArrayDeque<>();
        ClientHttpRequestExecution execution = (request, body) -> {
            attempts.incrementAndGet();
            TrackedResponse response = new TrackedResponse(
                    issued.size() < 2 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK);
            issued.add(response);
            return response;
        };

        ClientHttpResponse last = interceptor(3, false)
                .intercept(request(HttpMethod.GET), new byte[0], execution);

        assertThat(issued).hasSize(3);
        // Every discarded attempt is closed; the one handed back is not.
        assertThat(issued.stream().limit(2).map(TrackedResponse::isClosed)).containsOnly(true);
        assertThat(((TrackedResponse) last).isClosed()).isFalse();
    }

    private RetryingClientHttpRequestInterceptor interceptor(int maxAttempts, boolean retryNonIdempotent) {
        RetryConfig config = RetryConfig.<ClientHttpResponse>custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(IOException.class)
                .retryOnResult(RetryingClientHttpRequestInterceptor::isRetryableResponse)
                .build();
        return new RetryingClientHttpRequestInterceptor(Retry.of("test", config), retryNonIdempotent);
    }

    private ClientHttpRequestExecution responses(HttpStatus... statuses) {
        List<HttpStatus> ordered = List.of(statuses);
        return (request, body) -> {
            int attempt = attempts.getAndIncrement();
            return new MockClientHttpResponse(new byte[0], ordered.get(Math.min(attempt, ordered.size() - 1)));
        };
    }

    private static MockClientHttpRequest request(HttpMethod method) {
        return new MockClientHttpRequest(method, URI.create("http://inventory-service/api/stock"));
    }

    /** A response that remembers whether anyone closed it. */
    private static class TrackedResponse extends MockClientHttpResponse {

        private boolean closed;

        TrackedResponse(HttpStatus status) {
            super(new byte[0], status);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }
}

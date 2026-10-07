package org.platform.commons.client;

import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retries outgoing calls that failed in a way a retry could fix: a transport
 * error or a 5xx response.
 * <p>
 * This has to be the last interceptor on the builder. Spring walks the
 * interceptor chain with a one-shot iterator, so only the last link re-runs
 * the actual HTTP call when it calls {@code execute} again; from any earlier
 * position a retry would silently skip the interceptors behind it.
 */
public class RetryingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<HttpMethod> IDEMPOTENT_METHODS =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HttpMethod.DELETE,
                    HttpMethod.OPTIONS, HttpMethod.TRACE);

    private final Retry retry;
    private final boolean retryNonIdempotentMethods;

    public RetryingClientHttpRequestInterceptor(Retry retry, boolean retryNonIdempotentMethods) {
        this.retry = retry;
        this.retryNonIdempotentMethods = retryNonIdempotentMethods;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        if (!shouldRetry(request.getMethod())) {
            return execution.execute(request, body);
        }

        // Each attempt leaves a response behind. If we go round again we have
        // to close the one we are abandoning or its connection leaks.
        AtomicReference<ClientHttpResponse> abandoned = new AtomicReference<>();
        CheckedSupplier<ClientHttpResponse> attempt = () -> {
            ClientHttpResponse previous = abandoned.getAndSet(null);
            if (previous != null) {
                previous.close();
            }
            ClientHttpResponse response = execution.execute(request, body);
            abandoned.set(response);
            return response;
        };

        try {
            return retry.executeCheckedSupplier(attempt);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Outgoing call failed", t);
        }
    }

    private boolean shouldRetry(HttpMethod method) {
        return retryNonIdempotentMethods || IDEMPOTENT_METHODS.contains(method);
    }

    /**
     * Whether a response that arrived intact is still worth retrying. A 4xx
     * never is: the request itself is the problem.
     */
    public static boolean isRetryableResponse(ClientHttpResponse response) {
        try {
            return response.getStatusCode().is5xxServerError();
        } catch (IOException e) {
            return false;
        }
    }
}

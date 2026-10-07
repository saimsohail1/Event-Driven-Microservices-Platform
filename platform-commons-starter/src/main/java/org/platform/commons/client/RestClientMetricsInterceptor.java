package org.platform.commons.client;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Times every call made through the shared {@code RestClient}.
 * <p>
 * The URI path is deliberately not a tag. An interceptor only sees the
 * resolved URI, so tagging it would create a new time series per order id and
 * blow up the metric's cardinality.
 */
public class RestClientMetricsInterceptor implements ClientHttpRequestInterceptor {

    static final String METRIC_NAME = "platform.http.client.requests";

    private final MeterRegistry registry;

    public RestClientMetricsInterceptor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        long startedAt = System.nanoTime();
        String status = "IO_ERROR";
        String outcome = "IO_ERROR";
        try {
            ClientHttpResponse response = execution.execute(request, body);
            int code = response.getStatusCode().value();
            status = String.valueOf(code);
            outcome = outcomeOf(code);
            return response;
        } finally {
            registry.timer(METRIC_NAME,
                            "method", request.getMethod().name(),
                            "host", hostOf(request),
                            "status", status,
                            "outcome", outcome)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    private static String outcomeOf(int status) {
        return switch (status / 100) {
            case 1 -> "INFORMATIONAL";
            case 2 -> "SUCCESS";
            case 3 -> "REDIRECTION";
            case 4 -> "CLIENT_ERROR";
            case 5 -> "SERVER_ERROR";
            default -> "UNKNOWN";
        };
    }

    private static String hostOf(HttpRequest request) {
        String host = request.getURI().getHost();
        return host != null ? host : "unknown";
    }
}

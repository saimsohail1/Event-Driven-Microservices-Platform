package org.platform.commons.client;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Copies the current request's correlation id onto outgoing calls, so a single
 * id follows a request across service boundaries.
 */
public class CorrelationIdPropagatingInterceptor implements ClientHttpRequestInterceptor {

    private final String headerName;
    private final String mdcKey;

    public CorrelationIdPropagatingInterceptor(String headerName, String mdcKey) {
        this.headerName = headerName;
        this.mdcKey = mdcKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        String correlationId = MDC.get(mdcKey);
        // Never overwrite an id the caller set explicitly.
        if (correlationId != null && !request.getHeaders().containsKey(headerName)) {
            request.getHeaders().add(headerName, correlationId);
        }
        return execution.execute(request, body);
    }
}

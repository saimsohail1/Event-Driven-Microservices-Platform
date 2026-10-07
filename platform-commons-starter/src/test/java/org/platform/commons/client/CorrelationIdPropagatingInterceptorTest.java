package org.platform.commons.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdPropagatingInterceptorTest {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final CorrelationIdPropagatingInterceptor interceptor =
            new CorrelationIdPropagatingInterceptor(HEADER, MDC_KEY);

    private final ClientHttpRequestExecution execution = (request, body) ->
            new MockClientHttpResponse(new byte[0], 200);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockClientHttpRequest newRequest() {
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setMethod(HttpMethod.GET);
        return request;
    }

    @Test
    void forwardsTheCurrentCorrelationId() throws IOException {
        MDC.put(MDC_KEY, "request-99");
        MockClientHttpRequest request = newRequest();

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(HEADER)).isEqualTo("request-99");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void addsNoHeaderWhenThereIsNoCorrelationId() throws IOException {
        MockClientHttpRequest request = newRequest();

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().containsKey(HEADER)).isFalse();
    }

    @Test
    void doesNotOverwriteAnIdSetExplicitlyOnTheCall() throws IOException {
        MDC.put(MDC_KEY, "ambient-id");
        MockClientHttpRequest request = newRequest();
        request.getHeaders().add(HEADER, "explicit-id");

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().get(HEADER)).containsExactly("explicit-id");
    }
}

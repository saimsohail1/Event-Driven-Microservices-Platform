package org.platform.commons.client;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class RestClientMetricsInterceptorTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RestClientMetricsInterceptor interceptor = new RestClientMetricsInterceptor(registry);

    @Test
    void recordsASuccessfulCall() throws IOException {
        interceptor.intercept(request(HttpMethod.GET, "http://inventory-service/api/stock"), new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        Timer timer = registry.get(RestClientMetricsInterceptor.METRIC_NAME).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("method")).isEqualTo("GET");
        assertThat(timer.getId().getTag("host")).isEqualTo("inventory-service");
        assertThat(timer.getId().getTag("status")).isEqualTo("200");
        assertThat(timer.getId().getTag("outcome")).isEqualTo("SUCCESS");
    }

    @Test
    void separatesClientErrorsFromServerErrors() throws IOException {
        interceptor.intercept(request(HttpMethod.GET, "http://inventory-service/api/stock"), new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND));
        interceptor.intercept(request(HttpMethod.GET, "http://inventory-service/api/stock"), new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.BAD_GATEWAY));

        assertThat(registry.get(RestClientMetricsInterceptor.METRIC_NAME)
                .tag("outcome", "CLIENT_ERROR").timer().count()).isEqualTo(1);
        assertThat(registry.get(RestClientMetricsInterceptor.METRIC_NAME)
                .tag("outcome", "SERVER_ERROR").timer().count()).isEqualTo(1);
    }

    @Test
    void recordsCallsThatNeverGotAResponse() {
        assertThatIOException().isThrownBy(() ->
                interceptor.intercept(request(HttpMethod.POST, "http://payment-service/api/payments"), new byte[0],
                        (req, body) -> {
                            throw new IOException("connection refused");
                        }));

        Timer timer = registry.get(RestClientMetricsInterceptor.METRIC_NAME).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("status")).isEqualTo("IO_ERROR");
        assertThat(timer.getId().getTag("outcome")).isEqualTo("IO_ERROR");
    }

    @Test
    void keepsThePathOutOfTheTagsSoCardinalityStaysBounded() throws IOException {
        for (int i = 0; i < 25; i++) {
            interceptor.intercept(request(HttpMethod.GET, "http://order-service/api/orders/" + i), new byte[0],
                    (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        }

        assertThat(registry.get(RestClientMetricsInterceptor.METRIC_NAME).meters()).hasSize(1);
        assertThat(registry.get(RestClientMetricsInterceptor.METRIC_NAME).timer().count()).isEqualTo(25);
    }

    private static MockClientHttpRequest request(HttpMethod method, String uri) {
        return new MockClientHttpRequest(method, URI.create(uri));
    }
}

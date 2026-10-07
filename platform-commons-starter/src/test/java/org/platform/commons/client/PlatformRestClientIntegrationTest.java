package org.platform.commons.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.platform.commons.autoconfigure.PlatformRestClientAutoConfiguration;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the whole interceptor chain against a real socket. The unit tests
 * drive each interceptor in isolation, which cannot show that a retry
 * re-runs the actual request once Spring has assembled the chain.
 */
class PlatformRestClientIntegrationTest {

    private final List<String> receivedCorrelationIds = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    PlatformRestClientAutoConfiguration.class))
            .withUserConfiguration(MeterRegistryConfiguration.class)
            .withPropertyValues("platform.rest-client.retry.wait-duration=10ms");

    @AfterEach
    void stopServer() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void resetState() {
        receivedCorrelationIds.clear();
    }

    @Test
    void retriesAServerErrorAndSucceedsOnTheSecondAttempt() throws IOException {
        startServer(500, 200);

        runner.run(context -> {
            String body = context.getBean(RestClient.class).get().uri(baseUrl).retrieve().body(String.class);

            assertThat(body).isEqualTo("ok");
            assertThat(receivedCorrelationIds).hasSize(2);
        });
    }

    @Test
    void forwardsTheCorrelationIdOnEveryAttempt() throws IOException {
        startServer(503, 200);
        MDC.put("correlationId", "abc-123");

        runner.run(context -> {
            context.getBean(RestClient.class).get().uri(baseUrl).retrieve().body(String.class);

            assertThat(receivedCorrelationIds).containsExactly("abc-123", "abc-123");
        });
    }

    @Test
    void timesTheWholeCallOnceRatherThanEachAttempt() throws IOException {
        startServer(500, 200);

        runner.run(context -> {
            context.getBean(RestClient.class).get().uri(baseUrl).retrieve().body(String.class);

            // One sample, tagged with the outcome the caller actually saw.
            assertThat(context.getBean(MeterRegistry.class)
                    .get(RestClientMetricsInterceptor.METRIC_NAME).timer().count()).isEqualTo(1);
            assertThat(context.getBean(MeterRegistry.class)
                    .get(RestClientMetricsInterceptor.METRIC_NAME).timer().getId().getTag("outcome"))
                    .isEqualTo("SUCCESS");
        });
    }

    /** Replies with the given statuses in turn, one per request. */
    private void startServer(int... statuses) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int index = Math.min(receivedCorrelationIds.size(), statuses.length - 1);
            receivedCorrelationIds.add(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            respond(exchange, statuses[index]);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        byte[] body = (status < 400 ? "ok" : "boom").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}

package org.platform.commons.autoconfigure;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.platform.commons.client.CorrelationIdPropagatingInterceptor;
import org.platform.commons.client.RestClientMetricsInterceptor;
import org.platform.commons.client.RetryingClientHttpRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRestClientAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    PlatformRestClientAutoConfiguration.class));

    @Test
    void registersTheClientAndInterceptorByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context).hasSingleBean(CorrelationIdPropagatingInterceptor.class);
        });
    }

    @Test
    void appliesDefaultTimeoutsWhenNoneAreConfigured() {
        runner.run(context -> {
            PlatformProperties.RestClient properties = context.getBean(PlatformProperties.class).getRestClient();

            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void bindsConfiguredTimeouts() {
        runner.withPropertyValues(
                        "platform.rest-client.connect-timeout=750ms",
                        "platform.rest-client.read-timeout=30s")
                .run(context -> {
                    PlatformProperties.RestClient properties =
                            context.getBean(PlatformProperties.class).getRestClient();

                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(750));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnClient() {
        runner.withUserConfiguration(CustomRestClientConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context.getBean(RestClient.class)).isSameAs(context.getBean("customRestClient"));
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnInterceptor() {
        runner.withUserConfiguration(CustomInterceptorConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CorrelationIdPropagatingInterceptor.class);
            assertThat(context.getBean(CorrelationIdPropagatingInterceptor.class))
                    .isSameAs(context.getBean("customInterceptor"));
        });
    }

    @Test
    void registersTheRetryByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Retry.class);
            assertThat(context).hasSingleBean(RetryingClientHttpRequestInterceptor.class);

            RetryConfig config = context.getBean(Retry.class).getRetryConfig();
            assertThat(config.getMaxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void bindsConfiguredRetryValues() {
        runner.withPropertyValues(
                        "platform.rest-client.retry.max-attempts=5",
                        "platform.rest-client.retry.wait-duration=1s",
                        "platform.rest-client.retry.retry-non-idempotent-methods=true")
                .run(context -> {
                    PlatformProperties.Retry retry =
                            context.getBean(PlatformProperties.class).getRestClient().getRetry();

                    assertThat(retry.getMaxAttempts()).isEqualTo(5);
                    assertThat(retry.getWaitDuration()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(retry.isRetryNonIdempotentMethods()).isTrue();
                    assertThat(context.getBean(Retry.class).getRetryConfig().getMaxAttempts()).isEqualTo(5);
                });
    }

    @Test
    void retryCanBeDisabled() {
        runner.withPropertyValues("platform.rest-client.retry.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(Retry.class);
            assertThat(context).doesNotHaveBean(RetryingClientHttpRequestInterceptor.class);
            // The client itself still has to be there.
            assertThat(context).hasSingleBean(RestClient.class);
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnRetry() {
        runner.withUserConfiguration(CustomRetryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(Retry.class);
            assertThat(context.getBean(Retry.class)).isSameAs(context.getBean("platformRestClientRetry"));
            assertThat(context.getBean(Retry.class).getRetryConfig().getMaxAttempts()).isEqualTo(9);
        });
    }

    @Test
    void registersTheMetricsInterceptorOnlyWhenARegistryIsPresent() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RestClientMetricsInterceptor.class));

        runner.withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(RestClientMetricsInterceptor.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnMetricsInterceptor() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class, CustomMetricsInterceptorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClientMetricsInterceptor.class);
                    assertThat(context.getBean(RestClientMetricsInterceptor.class))
                            .isSameAs(context.getBean("customMetricsInterceptor"));
                });
    }

    @Configuration
    static class CustomRestClientConfiguration {

        @Bean
        RestClient customRestClient() {
            return RestClient.create();
        }
    }

    @Configuration
    static class CustomInterceptorConfiguration {

        @Bean
        CorrelationIdPropagatingInterceptor customInterceptor() {
            return new CorrelationIdPropagatingInterceptor("X-Custom", "custom");
        }
    }

    @Configuration
    static class CustomRetryConfiguration {

        @Bean
        Retry platformRestClientRetry() {
            return Retry.of("service-owned", RetryConfig.custom().maxAttempts(9).build());
        }
    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    static class CustomMetricsInterceptorConfiguration {

        @Bean
        RestClientMetricsInterceptor customMetricsInterceptor(MeterRegistry registry) {
            return new RestClientMetricsInterceptor(registry);
        }
    }
}

package org.platform.commons.autoconfigure;

import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.platform.commons.client.CorrelationIdPropagatingInterceptor;
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
}

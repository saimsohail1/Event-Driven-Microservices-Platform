package org.platform.commons.autoconfigure;

import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.platform.commons.web.RateLimitFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRateLimitAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PlatformRateLimitAutoConfiguration.class));

    @Test
    void registersTheFilterByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RateLimitFilter.class);
            PlatformProperties.RateLimit limit = context.getBean(PlatformProperties.class).getRateLimit();
            assertThat(limit.getRequests()).isEqualTo(100);
            assertThat(limit.getWindow()).isEqualTo(Duration.ofMinutes(1));
        });
    }

    @Test
    void bindsConfiguredValues() {
        runner.withPropertyValues(
                        "platform.rate-limit.requests=20",
                        "platform.rate-limit.window=30s")
                .run(context -> {
                    PlatformProperties.RateLimit limit = context.getBean(PlatformProperties.class).getRateLimit();
                    assertThat(limit.getRequests()).isEqualTo(20);
                    assertThat(limit.getWindow()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("platform.rate-limit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RateLimitFilter.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnFilter() {
        runner.withUserConfiguration(CustomFilterConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(RateLimitFilter.class);
            assertThat(context.getBean(RateLimitFilter.class)).isSameAs(context.getBean("customRateLimitFilter"));
        });
    }

    @Test
    void doesNotApplyOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        PlatformRateLimitAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RateLimitFilter.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterConfiguration {

        @Bean
        RateLimitFilter customRateLimitFilter() {
            PlatformProperties.RateLimit properties = new PlatformProperties.RateLimit();
            properties.setRequests(1);
            return new RateLimitFilter(properties, "correlationId", new com.fasterxml.jackson.databind.ObjectMapper());
        }
    }
}

package org.platform.commons.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformMetricsAutoConfiguration.class));

    @Test
    void registersCommonTagsCustomizerByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(MeterRegistryCustomizer.class));
    }

    @Test
    void tagsEveryMeterWithServiceAndEnvironment() {
        runner.withPropertyValues(
                        "platform.metrics.service-name=order-service",
                        "platform.metrics.environment=production")
                .run(context -> {
                    MeterRegistry registry = applyCustomizer(context.getBean(MeterRegistryCustomizer.class));

                    registry.counter("orders.placed").increment();

                    assertThat(registry.get("orders.placed").counter().getId().getTags())
                            .anyMatch(tag -> tag.getKey().equals("service") && tag.getValue().equals("order-service"))
                            .anyMatch(tag -> tag.getKey().equals("environment") && tag.getValue().equals("production"));
                });
    }

    @Test
    void fallsBackToApplicationNameWhenServiceNameIsNotSet() {
        runner.withPropertyValues("spring.application.name=payment-service")
                .run(context -> {
                    MeterRegistry registry = applyCustomizer(context.getBean(MeterRegistryCustomizer.class));

                    registry.counter("payments.captured").increment();

                    assertThat(registry.get("payments.captured").counter().getId().getTag("service"))
                            .isEqualTo("payment-service");
                });
    }

    @Test
    void defaultsToLocalEnvironmentAndUnknownService() {
        runner.run(context -> {
            MeterRegistry registry = applyCustomizer(context.getBean(MeterRegistryCustomizer.class));

            registry.counter("some.counter").increment();

            assertThat(registry.get("some.counter").counter().getId().getTag("service")).isEqualTo("unknown");
            assertThat(registry.get("some.counter").counter().getId().getTag("environment")).isEqualTo("local");
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnCustomizer() {
        runner.withUserConfiguration(CustomTagsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MeterRegistryCustomizer.class);
                    assertThat(context.getBean(MeterRegistryCustomizer.class))
                            .isSameAs(context.getBean("platformCommonTagsCustomizer"));
                });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("platform.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MeterRegistryCustomizer.class));
    }

    /**
     * The starter ships {@code micrometer-registry-prometheus}, so Boot's
     * Prometheus export auto-configuration is what actually scrapes. This
     * proves the common tags survive that path, not just a hand-applied
     * customizer.
     */
    @Test
    void commonTagsAppearOnThePrometheusScrape() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PlatformMetricsAutoConfiguration.class,
                        MetricsAutoConfiguration.class,
                        PrometheusMetricsExportAutoConfiguration.class))
                .withPropertyValues(
                        "platform.metrics.service-name=order-service",
                        "platform.metrics.environment=staging")
                .run(context -> {
                    assertThat(context).hasSingleBean(PrometheusMeterRegistry.class);

                    PrometheusMeterRegistry prometheus = context.getBean(PrometheusMeterRegistry.class);
                    prometheus.counter("orders.placed").increment();

                    String scrape = prometheus.scrape();
                    assertThat(scrape).contains("orders_placed_total");
                    assertThat(scrape).contains("service=\"order-service\"");
                    assertThat(scrape).contains("environment=\"staging\"");
                });
    }

    @SuppressWarnings("unchecked")
    private static MeterRegistry applyCustomizer(MeterRegistryCustomizer<MeterRegistry> customizer) {
        MeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);
        return registry;
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTagsConfiguration {

        // Overriding by name is the documented way to replace the common tags.
        @Bean
        MeterRegistryCustomizer<MeterRegistry> platformCommonTagsCustomizer() {
            return registry -> registry.config().commonTags("team", "payments");
        }
    }
}

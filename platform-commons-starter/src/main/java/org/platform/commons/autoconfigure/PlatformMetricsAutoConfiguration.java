package org.platform.commons.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.platform.commons.PlatformProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Tags every meter with the service it came from and the environment it ran
 * in. Without these, metrics from all three services arrive at Prometheus
 * indistinguishable from each other.
 */
@AutoConfiguration(before = {MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "platform.metrics", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformMetricsAutoConfiguration {

    /**
     * Conditional on the bean <em>name</em>, not the type. Services define
     * {@code MeterRegistryCustomizer} beans for unrelated reasons, and backing
     * off because one of those exists would silently drop the common tags.
     */
    @Bean
    @ConditionalOnMissingBean(name = "platformCommonTagsCustomizer")
    public MeterRegistryCustomizer<MeterRegistry> platformCommonTagsCustomizer(PlatformProperties properties,
                                                                               Environment environment) {
        PlatformProperties.Metrics metrics = properties.getMetrics();
        String serviceName = StringUtils.hasText(metrics.getServiceName())
                ? metrics.getServiceName()
                : environment.getProperty("spring.application.name", "unknown");

        return registry -> registry.config().commonTags(
                "service", serviceName,
                "environment", metrics.getEnvironment());
    }
}

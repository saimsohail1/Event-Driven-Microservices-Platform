package org.platform.commons.autoconfigure;

import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.platform.commons.kafka.KafkaHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlatformKafkaHealthAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    PlatformKafkaHealthAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    void registersTheHealthIndicatorByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(KafkaHealthIndicator.class);
            // The bean name is what the health endpoint keys the entry on.
            assertThat(context).hasBean("kafkaHealthIndicator");
            assertThat(context).hasSingleBean(Admin.class);
        });
    }

    @Test
    void defaultsToATwoSecondTimeout() {
        runner.run(context -> assertThat(timeoutIn(context)).isEqualTo(Duration.ofSeconds(2)));
    }

    @Test
    void bindsAConfiguredTimeout() {
        runner.withPropertyValues("platform.kafka.health.timeout=250ms")
                .run(context -> assertThat(timeoutIn(context)).isEqualTo(Duration.ofMillis(250)));
    }

    @Test
    void canBeDisabledThroughTheStandardActuatorProperty() {
        runner.withPropertyValues("management.health.kafka.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(KafkaHealthIndicator.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnIndicator() {
        runner.withUserConfiguration(CustomHealthIndicatorConfiguration.class).run(context -> {
            assertThat(context).doesNotHaveBean(KafkaHealthIndicator.class);
            assertThat(context).hasSingleBean(HealthIndicator.class);
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnAdminClient() {
        runner.withUserConfiguration(CustomAdminConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(Admin.class);
            assertThat(context.getBean(Admin.class)).isSameAs(context.getBean("serviceOwnedAdmin"));
        });
    }

    @Test
    void doesNothingWhenKafkaIsNotConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformKafkaHealthAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(KafkaHealthIndicator.class));
    }

    private static Duration timeoutIn(ApplicationContext context) {
        return context.getBean(PlatformProperties.class).getKafka().getHealth().getTimeout();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHealthIndicatorConfiguration {

        @Bean
        HealthIndicator kafkaHealthIndicator() {
            return () -> Health.up().build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAdminConfiguration {

        @Bean
        Admin serviceOwnedAdmin() {
            return mock(Admin.class);
        }
    }
}

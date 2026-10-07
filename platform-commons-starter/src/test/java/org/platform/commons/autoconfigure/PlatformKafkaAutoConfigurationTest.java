package org.platform.commons.autoconfigure;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.platform.commons.PlatformProperties;
import org.platform.commons.kafka.DeadLetterProducer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformKafkaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    PlatformKafkaAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    void registersTheKafkaDefaultsByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DefaultKafkaProducerFactoryCustomizer.class);
            assertThat(context).hasSingleBean(DefaultErrorHandler.class);
            assertThat(context).hasSingleBean(DeadLetterProducer.class);
            // Boot's producer template must still be there: a KafkaTemplate
            // bean of our own would make @ConditionalOnMissingBean skip it.
            assertThat(context).hasBean("kafkaTemplate");
        });
    }

    @Test
    void makesTheProducerSafeAgainstLossAndReordering() {
        runner.run(context -> {
            Map<String, Object> configs = customised(context, new HashMap<>());

            assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
            assertThat(configs).containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            assertThat(configs).containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        });
    }

    @Test
    void leavesTheWholeSafetySetAloneOnceTheServiceTouchesAnyOfIt() {
        runner.run(context -> {
            Map<String, Object> chosenByTheService = new HashMap<>();
            chosenByTheService.put(ProducerConfig.ACKS_CONFIG, "1");

            Map<String, Object> configs = customised(context, chosenByTheService);

            // Mixing acks=1 with enable.idempotence=true is a ConfigException
            // at producer start, so the three settings move as one group.
            assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "1");
            assertThat(configs).doesNotContainKey(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG);
            assertThat(configs).doesNotContainKey(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION);
        });
    }

    @Test
    void producerDefaultsCanBeTurnedOff() {
        runner.withPropertyValues("platform.kafka.producer.apply-defaults=false")
                .run(context -> assertThat(context).doesNotHaveBean(DefaultKafkaProducerFactoryCustomizer.class));
    }

    @Test
    void bindsConfiguredConsumerValues() {
        runner.withPropertyValues(
                        "platform.kafka.consumer.max-attempts=5",
                        "platform.kafka.consumer.backoff=500ms",
                        "platform.kafka.consumer.dead-letter-suffix=.dead")
                .run(context -> {
                    PlatformProperties.KafkaConsumer consumer =
                            context.getBean(PlatformProperties.class).getKafka().getConsumer();

                    assertThat(consumer.getMaxAttempts()).isEqualTo(5);
                    assertThat(consumer.getBackoff()).isEqualTo(Duration.ofMillis(500));
                    assertThat(consumer.getDeadLetterSuffix()).isEqualTo(".dead");
                });
    }

    @Test
    void defaultsToThreeDeliveriesTwoSecondsApart() {
        runner.run(context -> {
            PlatformProperties.KafkaConsumer consumer =
                    context.getBean(PlatformProperties.class).getKafka().getConsumer();

            assertThat(consumer.getMaxAttempts()).isEqualTo(3);
            assertThat(consumer.getBackoff()).isEqualTo(Duration.ofSeconds(2));
            // FixedBackOff counts retries, so three deliveries means two.
            assertThat(new FixedBackOff(consumer.getBackoff().toMillis(), consumer.getMaxAttempts() - 1).getMaxAttempts())
                    .isEqualTo(2);
        });
    }

    @Test
    void startsEvenWhenANonRetryableExceptionCannotBeResolved() {
        runner.withPropertyValues(
                        "platform.kafka.consumer.non-retryable-exceptions[0]=java.lang.IllegalStateException",
                        "platform.kafka.consumer.non-retryable-exceptions[1]=com.example.NoSuchException")
                .run(context -> {
                    // A typo in a property must never stop a service booting.
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DefaultErrorHandler.class);
                });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnErrorHandler() {
        runner.withUserConfiguration(CustomErrorHandlerConfiguration.class).run(context -> {
            assertThat(context).doesNotHaveBean(DefaultErrorHandler.class);
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnDeadLetterProducer() {
        runner.withUserConfiguration(CustomDeadLetterProducerConfiguration.class).run(context ->
                assertThat(context.getBean(DeadLetterProducer.class))
                        .isSameAs(context.getBean("serviceOwnedDeadLetter")));
    }

    @Test
    void canBeDisabledEntirely() {
        runner.withPropertyValues("platform.kafka.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DefaultKafkaProducerFactoryCustomizer.class);
            assertThat(context).doesNotHaveBean(DefaultErrorHandler.class);
            assertThat(context).doesNotHaveBean(DeadLetterProducer.class);
        });
    }

    /** Runs the customizer over a factory holding whatever the service set. */
    private static Map<String, Object> customised(ApplicationContext context, Map<String, Object> serviceConfigs) {
        DefaultKafkaProducerFactory<?, ?> factory = new DefaultKafkaProducerFactory<>(serviceConfigs);
        context.getBean(DefaultKafkaProducerFactoryCustomizer.class).customize(factory);
        return factory.getConfigurationProperties();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomErrorHandlerConfiguration {

        @Bean
        CommonErrorHandler serviceOwnedErrorHandler() {
            return new CommonLoggingErrorHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDeadLetterProducerConfiguration {

        @Bean
        DeadLetterProducer serviceOwnedDeadLetter() {
            return new DeadLetterProducer(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of())));
        }
    }
}

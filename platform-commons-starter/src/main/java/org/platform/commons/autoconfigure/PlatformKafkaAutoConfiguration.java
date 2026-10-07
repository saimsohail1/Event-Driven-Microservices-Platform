package org.platform.commons.autoconfigure;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.platform.commons.PlatformProperties;
import org.platform.commons.kafka.DeadLetterProducer;
import org.platform.commons.kafka.DeadLetterTopicResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.ClassUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Kafka conventions every service in the platform is expected to follow:
 * a producer that cannot silently lose or reorder writes, and a consumer that
 * parks records it cannot process instead of retrying them forever.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, DefaultErrorHandler.class})
@ConditionalOnProperty(prefix = "platform.kafka", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformKafkaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformKafkaAutoConfiguration.class);

    /**
     * Applies the safe-producer settings, but only to keys the service has not
     * set itself. These are defaults, not overrides: a service that has
     * deliberately chosen {@code acks=1} keeps it.
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.kafka.producer", name = "apply-defaults", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "platformProducerDefaultsCustomizer")
    public DefaultKafkaProducerFactoryCustomizer platformProducerDefaultsCustomizer() {
        return factory -> {
            Map<String, Object> existing = factory.getConfigurationProperties();
            // These three settings are a single contract. Applying only the
            // missing ones would combine a service's acks=1 with our
            // enable.idempotence=true, which Kafka rejects at start-up.
            if (existing.containsKey(ProducerConfig.ACKS_CONFIG)
                    || existing.containsKey(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)
                    || existing.containsKey(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION)) {
                return;
            }

            Map<String, Object> defaults = new HashMap<>();
            // Without acks=all an in-sync replica failure loses writes.
            defaults.put(ProducerConfig.ACKS_CONFIG, "all");
            // Idempotence makes the broker drop the duplicates a producer
            // retry would otherwise append.
            defaults.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            // 5 is the highest value the broker can still de-duplicate and
            // keep in order; above it a retry can reorder a partition.
            defaults.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
            factory.updateConfigs(defaults);
        };
    }

    /**
     * A byte[] template, so a record that failed because it could not be
     * deserialized can still be forwarded verbatim.
     * <p>
     * Wrapped in {@link DeadLetterProducer} so it does not count as a
     * {@link KafkaTemplate} bean. Boot would otherwise skip the service's
     * own producer template.
     * <p>
     * Only the broker list is carried over from {@code spring.kafka}. A
     * service talking to a TLS or SASL cluster should define its own bean.
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterProducer.class)
    public DeadLetterProducer deadLetterProducer(KafkaProperties kafkaProperties) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DeadLetterProducer(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs)));
    }

    /**
     * Conditional on {@link CommonErrorHandler}, the type the listener
     * container actually consumes, so a service that declares any error
     * handler of its own replaces this one outright.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterProducer deadLetterProducer,
                                                 PlatformProperties properties) {

        PlatformProperties.KafkaConsumer consumer = properties.getKafka().getConsumer();

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterProducer.template(), new DeadLetterTopicResolver(consumer.getDeadLetterSuffix()));

        // maxAttempts counts deliveries, so one plus this many retries.
        FixedBackOff backOff = new FixedBackOff(consumer.getBackoff().toMillis(),
                Math.max(0, consumer.getMaxAttempts() - 1));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        Class<? extends Exception>[] nonRetryable = resolve(consumer.getNonRetryableExceptions());
        if (nonRetryable.length > 0) {
            handler.addNotRetryableExceptions(nonRetryable);
        }
        return handler;
    }

    /**
     * Turns the configured class names into types, skipping anything that
     * will not load. A typo in a property must not stop the service booting.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Exception>[] resolve(List<String> classNames) {
        List<Class<? extends Exception>> resolved = new ArrayList<>();
        for (String className : classNames) {
            try {
                Class<?> candidate =
                        ClassUtils.forName(className, PlatformKafkaAutoConfiguration.class.getClassLoader());
                if (Exception.class.isAssignableFrom(candidate)) {
                    resolved.add((Class<? extends Exception>) candidate);
                } else {
                    log.warn("Ignoring non-retryable exception '{}': not an Exception", className);
                }
            } catch (ClassNotFoundException | LinkageError e) {
                log.warn("Ignoring non-retryable exception '{}': class not found", className);
            }
        }
        return resolved.toArray(new Class[0]);
    }
}

package org.paymentservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Failed messages are retried a few times and then moved to
 * {@code <topic>.DLT} rather than blocking the partition forever.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /**
     * Dead-letter records are republished as the original raw bytes, so this
     * template serializes pass-through byte arrays rather than JSON.
     */
    @Bean
    public KafkaTemplate<byte[], byte[]> deadLetterKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<byte[], byte[]> deadLetterKafkaTemplate,
            @Value("${app.kafka.retry.interval-ms:2000}") long retryIntervalMs,
            @Value("${app.kafka.retry.max-attempts:3}") long maxAttempts) {

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate),
                new FixedBackOff(retryIntervalMs, maxAttempts));

        // A malformed event will never deserialize correctly, so dead-letter it
        // immediately instead of burning retries.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}

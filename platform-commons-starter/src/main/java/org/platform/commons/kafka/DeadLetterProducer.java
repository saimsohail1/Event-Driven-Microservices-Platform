package org.platform.commons.kafka;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Holds the byte[] {@link KafkaTemplate} used to republish failed records.
 * It is not itself a {@code KafkaTemplate} bean: Spring Boot's
 * {@code @ConditionalOnMissingBean(KafkaTemplate.class)} would otherwise skip
 * the service's own producer template.
 */
public class DeadLetterProducer implements DisposableBean {

    private final KafkaTemplate<byte[], byte[]> template;

    public DeadLetterProducer(KafkaTemplate<byte[], byte[]> template) {
        this.template = template;
    }

    public KafkaTemplate<byte[], byte[]> template() {
        return template;
    }

    @Override
    public void destroy() {
        template.destroy();
    }
}

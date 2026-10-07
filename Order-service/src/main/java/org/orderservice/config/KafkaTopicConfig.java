package org.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Order service owns the {@code order.created} contract, so it declares the
 * topic and its dead-letter counterpart rather than relying on broker-side
 * topic auto-creation.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.order-created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.partitions:3}")
    private int partitions;

    @Value("${app.kafka.topics.replicas:1}")
    private short replicas;

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(orderCreatedTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic orderCreatedDeadLetterTopic() {
        return TopicBuilder.name(orderCreatedTopic + ".DLT")
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}

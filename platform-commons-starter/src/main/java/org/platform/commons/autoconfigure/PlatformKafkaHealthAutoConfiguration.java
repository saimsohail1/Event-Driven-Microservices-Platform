package org.platform.commons.autoconfigure;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.platform.commons.PlatformProperties;
import org.platform.commons.kafka.KafkaHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Registers the Kafka health indicator, under the {@code kafka} key.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({Admin.class, HealthIndicator.class})
@ConditionalOnBean(KafkaAdmin.class)
@ConditionalOnEnabledHealthIndicator("kafka")
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformKafkaHealthAutoConfiguration {

    /**
     * One long-lived client. Creating an admin client per check would open a
     * connection and start a thread every few seconds for as long as the
     * readiness probe runs.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Admin.class)
    public Admin platformKafkaAdmin(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @Bean
    @ConditionalOnMissingBean(name = "kafkaHealthIndicator")
    public KafkaHealthIndicator kafkaHealthIndicator(Admin admin, PlatformProperties properties) {
        return new KafkaHealthIndicator(admin, properties.getKafka().getHealth().getTimeout());
    }
}

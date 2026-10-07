package org.paymentservice;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.paymentservice.entity.PaymentStatus;
import org.paymentservice.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the listener against a real broker, including the deserializer
 * chain: raw JSON on the topic has to become an OrderCreatedEvent and settle a
 * payment. This is what catches a broken serializer configuration, which would
 * otherwise only show up at runtime.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.created"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentEventConsumptionIntegrationTest {

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void settlesPaymentFromAJsonEventOnTheTopic() {
        UUID orderId = UUID.randomUUID();
        String payload = """
                {"orderId":"%s","productId":"PROD-INT","quantity":3,"price":19.99}
                """.formatted(orderId);

        sendRawEvent(orderId.toString(), payload);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findByOrderId(orderId))
                        .get()
                        .satisfies(payment -> {
                            assertThat(payment.getAmount()).isEqualByComparingTo("59.97");
                            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                        }));
    }

    private void sendRawEvent(String key, String payload) {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(producerProps);
        try {
            new KafkaTemplate<>(factory).send("order.created", key, payload);
        } finally {
            factory.destroy();
        }
    }
}

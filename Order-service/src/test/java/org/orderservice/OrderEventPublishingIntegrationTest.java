package org.orderservice;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.orderservice.dto.CreateOrderRequest;
import org.orderservice.entity.Orders;
import org.orderservice.outbox.OutboxEventRepository;
import org.orderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end check of the outbox against a real in-process broker: an order
 * committed through the service must appear on {@code order.created}, keyed by
 * its order id, and the outbox row must be marked published.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.outbox.initial-delay-ms=0",
        "app.outbox.poll-interval-ms=200",
        "app.kafka.topics.partitions=1",
        "app.kafka.topics.replicas=1"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.created"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OrderEventPublishingIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void committedOrderIsPublishedToKafkaAndDrainedFromTheOutbox() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("order-test-group", "true", broker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "order.created");

            CreateOrderRequest request = new CreateOrderRequest();
            request.setProductId("PROD-001");
            request.setQuantity(5);
            request.setPrice(new BigDecimal("99.99"));

            Orders order = orderService.createOrder(request);

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "order.created", Duration.ofSeconds(20));

            // Keyed by order id so events for one order keep their order.
            assertThat(record.key()).isEqualTo(order.getId().toString());
            assertThat(record.value())
                    .contains("\"orderId\":\"" + order.getId() + "\"")
                    .contains("\"productId\":\"PROD-001\"")
                    .contains("\"quantity\":5")
                    .contains("\"price\":99.99");

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());
        }
    }
}

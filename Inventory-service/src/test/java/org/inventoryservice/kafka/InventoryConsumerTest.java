package org.inventoryservice.kafka;

import org.inventoryservice.entity.InventoryItem;
import org.inventoryservice.event.OrderCreatedEvent;
import org.inventoryservice.exception.InsufficientStockException;
import org.inventoryservice.exception.UnknownProductException;
import org.inventoryservice.repository.InventoryRepository;
import org.inventoryservice.repository.ProcessedOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class InventoryConsumerTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    private InventoryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryConsumer(inventoryRepository, processedOrderRepository);
        InventoryItem item = new InventoryItem();
        item.setProductId("PROD-001");
        item.setAvailableQuantity(10);
        inventoryRepository.saveAndFlush(item);
    }

    private OrderCreatedEvent event(UUID orderId, String productId, int quantity) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setProductId(productId);
        event.setQuantity(quantity);
        event.setPrice(new BigDecimal("9.99"));
        return event;
    }

    @Test
    void reservesStockForNewOrder() {
        consumer.consume(event(UUID.randomUUID(), "PROD-001", 4));

        assertThat(inventoryRepository.findById("PROD-001"))
                .get()
                .satisfies(item -> assertThat(item.getAvailableQuantity()).isEqualTo(6));
    }

    @Test
    void redeliveredEventDoesNotDecrementStockTwice() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = event(orderId, "PROD-001", 4);

        consumer.consume(event);
        consumer.consume(event);

        assertThat(inventoryRepository.findById("PROD-001"))
                .get()
                .satisfies(item -> assertThat(item.getAvailableQuantity()).isEqualTo(6));
        assertThat(processedOrderRepository.existsById(orderId)).isTrue();
    }

    @Test
    void rejectsOrderForUnknownProductInsteadOfInventingStock() {
        OrderCreatedEvent event = event(UUID.randomUUID(), "PROD-UNKNOWN", 1);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(UnknownProductException.class);

        assertThat(inventoryRepository.findById("PROD-UNKNOWN")).isEmpty();
    }

    @Test
    void refusesToDriveStockNegative() {
        OrderCreatedEvent event = event(UUID.randomUUID(), "PROD-001", 11);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(inventoryRepository.findById("PROD-001"))
                .get()
                .satisfies(item -> assertThat(item.getAvailableQuantity()).isEqualTo(10));
    }

    @Test
    void rejectsEventWithoutOrderId() {
        OrderCreatedEvent event = event(null, "PROD-001", 1);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

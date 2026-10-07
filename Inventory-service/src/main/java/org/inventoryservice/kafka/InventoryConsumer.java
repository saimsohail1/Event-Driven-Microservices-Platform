package org.inventoryservice.kafka;

import org.inventoryservice.entity.InventoryItem;
import org.inventoryservice.entity.ProcessedOrder;
import org.inventoryservice.event.OrderCreatedEvent;
import org.inventoryservice.exception.InsufficientStockException;
import org.inventoryservice.exception.UnknownProductException;
import org.inventoryservice.repository.InventoryRepository;
import org.inventoryservice.repository.ProcessedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    private final InventoryRepository repository;
    private final ProcessedOrderRepository processedOrders;

    public InventoryConsumer(InventoryRepository repository, ProcessedOrderRepository processedOrders) {
        this.repository = repository;
        this.processedOrders = processedOrders;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(OrderCreatedEvent event) {
        UUID orderId = event.getOrderId();
        if (orderId == null) {
            throw new IllegalArgumentException("OrderCreatedEvent is missing orderId");
        }

        // Kafka delivery is at-least-once, so the same event can arrive twice.
        if (processedOrders.existsById(orderId)) {
            log.debug("Order {} already applied to inventory, skipping", orderId);
            return;
        }

        InventoryItem item = repository.findById(event.getProductId())
                .orElseThrow(() -> new UnknownProductException(event.getProductId()));

        int remaining = item.getAvailableQuantity() - event.getQuantity();
        if (remaining < 0) {
            throw new InsufficientStockException(event.getProductId(), item.getAvailableQuantity(), event.getQuantity());
        }

        item.setAvailableQuantity(remaining);
        repository.save(item);
        processedOrders.save(new ProcessedOrder(orderId));

        log.info("Reserved {} unit(s) of {} for order {}, {} remaining",
                event.getQuantity(), event.getProductId(), orderId, remaining);
    }
}

package org.paymentservice.kafka;

import org.paymentservice.event.OrderCreatedEvent;
import org.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentService paymentService;

    public PaymentConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(OrderCreatedEvent event) {
        UUID orderId = event.getOrderId();
        if (orderId == null) {
            throw new IllegalArgumentException("OrderCreatedEvent is missing orderId");
        }

        BigDecimal amount = totalFor(event);

        // Kafka delivery is at-least-once, so a replay must not double-charge.
        paymentService.createPaymentIfAbsent(orderId, amount);

        log.info("Settled payment of {} for order {}", amount, orderId);
    }

    private BigDecimal totalFor(OrderCreatedEvent event) {
        BigDecimal unitPrice = event.getPrice();
        if (unitPrice == null) {
            throw new IllegalArgumentException("OrderCreatedEvent for order " + event.getOrderId() + " is missing price");
        }
        return unitPrice.multiply(BigDecimal.valueOf(event.getQuantity()));
    }
}

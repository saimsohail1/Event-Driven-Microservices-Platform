package org.paymentservice.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paymentservice.entity.Payment;
import org.paymentservice.entity.PaymentStatus;
import org.paymentservice.event.OrderCreatedEvent;
import org.paymentservice.repository.PaymentRepository;
import org.paymentservice.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PaymentConsumerTest {

    @Autowired
    private PaymentRepository repository;

    private PaymentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentConsumer(new PaymentService(repository));
    }

    private OrderCreatedEvent event(UUID orderId, int quantity, String unitPrice) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setProductId("PROD-001");
        event.setQuantity(quantity);
        event.setPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return event;
    }

    @Test
    void settlesOrderTotalRatherThanUnitPrice() {
        UUID orderId = UUID.randomUUID();

        consumer.consume(event(orderId, 3, "19.99"));

        assertThat(repository.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getAmount()).isEqualByComparingTo("59.97");
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                });
    }

    @Test
    void redeliveredEventDoesNotCreateASecondPayment() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = event(orderId, 2, "10.00");

        consumer.consume(event);
        consumer.consume(event);

        List<Payment> payments = repository.findAll();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsEventWithoutOrderId() {
        assertThatThrownBy(() -> consumer.consume(event(null, 1, "10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEventWithoutPrice() {
        assertThatThrownBy(() -> consumer.consume(event(UUID.randomUUID(), 1, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

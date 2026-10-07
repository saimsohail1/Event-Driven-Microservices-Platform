package org.orderservice.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.orderservice.entity.Orders;
import org.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the JPA mappings against the Flyway-created schema, so a drift
 * between a migration and an entity fails the build rather than production.
 */
@DataJpaTest
class OutboxPersistenceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void persistsOrderWithExactMonetaryScale() {
        Orders order = new Orders();
        order.setProductId("PROD-001");
        order.setQuantity(3);
        order.setPrice(new BigDecimal("19.99"));

        Orders saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(orderRepository.findById(saved.getId()))
                .get()
                .satisfies(found -> assertThat(found.getPrice()).isEqualByComparingTo("19.99"));
    }

    @Test
    void claimPendingReturnsOnlyUnpublishedEvents() {
        OutboxEvent pending = new OutboxEvent("Order", "order-1", "OrderCreatedEvent", "order.created", "{}");
        OutboxEvent published = new OutboxEvent("Order", "order-2", "OrderCreatedEvent", "order.created", "{}");
        published.markPublished();
        outboxRepository.saveAllAndFlush(List.of(pending, published));

        List<OutboxEvent> claimed = outboxRepository.claimPending(10, PageRequest.of(0, 10));

        assertThat(claimed).extracting(OutboxEvent::getAggregateId).containsExactly("order-1");
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1);
    }

    @Test
    void stopsClaimingEventsThatExhaustedTheirAttempts() {
        OutboxEvent poisoned = new OutboxEvent("Order", "order-3", "OrderCreatedEvent", "order.created", "{}");
        for (int i = 0; i < 10; i++) {
            poisoned.markFailed("broker down");
        }
        outboxRepository.saveAndFlush(poisoned);

        assertThat(outboxRepository.claimPending(10, PageRequest.of(0, 10))).isEmpty();
    }
}

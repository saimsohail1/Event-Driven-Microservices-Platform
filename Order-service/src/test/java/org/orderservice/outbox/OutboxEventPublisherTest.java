package org.orderservice.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEventPublisher publisher() {
        return new OutboxEventPublisher(repository, kafkaTemplate, 100, 10, 1000L);
    }

    private OutboxEvent pendingEvent(String aggregateId) {
        return new OutboxEvent("Order", aggregateId, "OrderCreatedEvent", "order.created", "{\"orderId\":\"" + aggregateId + "\"}");
    }

    @Test
    void marksEventsPublishedAndKeysThemByAggregateId() {
        OutboxEvent event = pendingEvent("order-1");
        when(repository.claimPending(anyInt(), any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("order.created"), eq("order-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void leavesEventPendingAndStopsBatchWhenBrokerFails() {
        OutboxEvent first = pendingEvent("order-1");
        OutboxEvent second = pendingEvent("order-2");
        when(repository.claimPending(anyInt(), any(Pageable.class))).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher().publishPending();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");

        // The rest of the batch is deferred rather than retried immediately.
        assertThat(second.getAttempts()).isZero();
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void doesNothingWhenOutboxIsEmpty() {
        when(repository.claimPending(anyInt(), any(Pageable.class))).thenReturn(List.of());

        publisher().publishPending();

        verify(kafkaTemplate, times(0)).send(any(), any(), any());
    }
}

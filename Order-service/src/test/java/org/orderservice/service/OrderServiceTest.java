package org.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.orderservice.dto.CreateOrderRequest;
import org.orderservice.entity.Orders;
import org.orderservice.event.OrderCreatedEvent;
import org.orderservice.outbox.OutboxEventRecorder;
import org.orderservice.repository.OrderRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String TOPIC = "order.created";

    @Mock
    private OrderRepository repository;

    @Mock
    private OutboxEventRecorder outbox;

    @Test
    void createOrderPersistsOrderAndRecordsOutboxEvent() {
        OrderService service = new OrderService(repository, outbox, TOPIC);
        UUID assignedId = UUID.randomUUID();
        when(repository.save(any(Orders.class))).thenAnswer(invocation -> {
            // Hibernate assigns the generated UUID during persist.
            Orders persisted = invocation.getArgument(0);
            ReflectionTestUtils.setField(persisted, "id", assignedId);
            return persisted;
        });

        CreateOrderRequest request = request("PROD-001", 5, "99.99");
        Orders created = service.createOrder(request);

        assertThat(created.getProductId()).isEqualTo("PROD-001");
        assertThat(created.getQuantity()).isEqualTo(5);
        assertThat(created.getPrice()).isEqualByComparingTo("99.99");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).record(eq("Order"), eq(assignedId.toString()), eq("OrderCreatedEvent"), eq(TOPIC), payload.capture());

        assertThat(payload.getValue()).isInstanceOf(OrderCreatedEvent.class);
        OrderCreatedEvent event = (OrderCreatedEvent) payload.getValue();
        assertThat(event.getProductId()).isEqualTo("PROD-001");
        assertThat(event.getQuantity()).isEqualTo(5);
        assertThat(event.getPrice()).isEqualByComparingTo("99.99");
    }

    private CreateOrderRequest request(String productId, int quantity, String price) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        request.setPrice(new BigDecimal(price));
        return request;
    }
}

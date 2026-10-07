package org.orderservice.service;

import org.orderservice.dto.CreateOrderRequest;
import org.orderservice.entity.Orders;
import org.orderservice.event.OrderCreatedEvent;
import org.orderservice.outbox.OutboxEventRecorder;
import org.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_TYPE = "OrderCreatedEvent";

    private final OrderRepository repository;
    private final OutboxEventRecorder outbox;
    private final String orderCreatedTopic;

    public OrderService(OrderRepository repository,
                        OutboxEventRecorder outbox,
                        @Value("${app.kafka.topics.order-created}") String orderCreatedTopic) {
        this.repository = repository;
        this.outbox = outbox;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    @Transactional
    public Orders createOrder(CreateOrderRequest request) {
        Orders order = new Orders();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setPrice(request.getPrice());

        Orders savedOrder = repository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(savedOrder.getId());
        event.setProductId(savedOrder.getProductId());
        event.setQuantity(savedOrder.getQuantity());
        event.setPrice(savedOrder.getPrice());

        // Same transaction as the order insert: the event cannot be lost, and it
        // cannot be seen by consumers before the order is committed.
        outbox.record(AGGREGATE_TYPE, savedOrder.getId().toString(), EVENT_TYPE, orderCreatedTopic, event);

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public List<Orders> getAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}

package org.orderservice.controller;

import jakarta.validation.Valid;
import org.orderservice.dto.CreateOrderRequest;
import org.orderservice.entity.Orders;
import org.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Orders> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<List<Orders>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }
}

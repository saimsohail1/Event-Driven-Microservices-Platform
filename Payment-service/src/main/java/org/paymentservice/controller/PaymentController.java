package org.paymentservice.controller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.paymentservice.entity.Payment;
import org.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Payment> pay(
            @RequestParam @NotNull UUID orderId,
            @RequestParam @NotNull @DecimalMin(value = "0.00", message = "amount must not be negative")
            @Digits(integer = 17, fraction = 2, message = "amount supports at most 2 decimal places") BigDecimal amount) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPayment(orderId, amount));
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }
}

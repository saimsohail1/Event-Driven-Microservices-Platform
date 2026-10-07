package org.paymentservice.service;

import org.junit.jupiter.api.Test;
import org.paymentservice.exception.PaymentAlreadyExistsException;
import org.paymentservice.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PaymentServiceTest {

    @Autowired
    private PaymentRepository repository;

    @Test
    void rejectsASecondManualPaymentForTheSameOrder() {
        PaymentService service = new PaymentService(repository);
        UUID orderId = UUID.randomUUID();

        service.createPayment(orderId, new BigDecimal("25.00"));

        assertThatThrownBy(() -> service.createPayment(orderId, new BigDecimal("25.00")))
                .isInstanceOf(PaymentAlreadyExistsException.class);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void storesMonetaryAmountsWithTwoDecimalPlaces() {
        PaymentService service = new PaymentService(repository);

        UUID orderId = UUID.randomUUID();
        service.createPayment(orderId, new BigDecimal("0.10").add(new BigDecimal("0.20")));

        assertThat(repository.findByOrderId(orderId))
                .get()
                .satisfies(payment -> assertThat(payment.getAmount()).isEqualByComparingTo("0.30"));
    }
}

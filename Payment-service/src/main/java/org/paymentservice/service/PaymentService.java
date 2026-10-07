package org.paymentservice.service;

import org.paymentservice.entity.Payment;
import org.paymentservice.entity.PaymentStatus;
import org.paymentservice.exception.PaymentAlreadyExistsException;
import org.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a payment for an order, rejecting a second attempt for the same
     * order.
     */
    @Transactional
    public Payment createPayment(UUID orderId, BigDecimal amount) {
        if (repository.existsByOrderId(orderId)) {
            throw new PaymentAlreadyExistsException(orderId);
        }
        return repository.save(newPayment(orderId, amount));
    }

    /**
     * Records a payment only if the order has none yet. Returns the existing
     * payment when the order has already been settled, which is what makes the
     * event consumer safe to replay.
     */
    @Transactional
    public Payment createPaymentIfAbsent(UUID orderId, BigDecimal amount) {
        Optional<Payment> existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.debug("Payment for order {} already exists, skipping", orderId);
            return existing.get();
        }
        return repository.save(newPayment(orderId, amount));
    }

    @Transactional(readOnly = true)
    public List<Payment> getAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Payment newPayment(UUID orderId, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.COMPLETED);
        return payment;
    }
}

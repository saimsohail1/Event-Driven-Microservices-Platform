package org.orderservice.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains the outbox to Kafka. Runs on a scheduler rather than on the request
 * thread, so a broker outage delays delivery instead of failing or losing it.
 */
@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final long sendTimeoutMs;

    public OutboxEventPublisher(OutboxEventRepository repository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${app.outbox.batch-size:100}") int batchSize,
                                @Value("${app.outbox.max-attempts:10}") int maxAttempts,
                                @Value("${app.outbox.send-timeout-ms:15000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-interval-ms:1000}",
            initialDelayString = "${app.outbox.initial-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.claimPending(maxAttempts, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                // The aggregate id is the message key, so all events for one
                // order land on the same partition and stay ordered.
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished();
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("Publisher thread interrupted");
                break;
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                if (event.getAttempts() >= maxAttempts) {
                    log.error("Giving up on outbox event {} ({}) after {} attempts: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), e.getMessage());
                } else {
                    log.warn("Failed to publish outbox event {} ({}), attempt {}: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), e.getMessage());
                }
                // Kafka is likely unavailable for the whole batch; retry next poll
                // instead of blocking on every remaining send.
                break;
            }
        }

        if (published > 0) {
            log.info("Published {} outbox event(s)", published);
        }
    }
}

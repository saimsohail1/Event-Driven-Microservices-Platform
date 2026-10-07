package org.orderservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Writes outbox rows. Must be called from inside the caller's transaction so
 * the event shares the fate of the business data.
 */
@Component
public class OutboxEventRecorder {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventRecorder(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent record(String aggregateType, String aggregateId, String eventType, String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize " + eventType + " for outbox", e);
        }
        return repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, topic, json));
    }
}

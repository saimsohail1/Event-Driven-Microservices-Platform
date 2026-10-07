package org.orderservice.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of unpublished events. The {@code -2} lock timeout is
     * Hibernate's SKIP_LOCKED, which lets several service replicas poll the
     * outbox concurrently without blocking each other or double-publishing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL AND e.attempts < :maxAttempts ORDER BY e.createdAt ASC")
    List<OutboxEvent> claimPending(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    long countByPublishedAtIsNull();
}

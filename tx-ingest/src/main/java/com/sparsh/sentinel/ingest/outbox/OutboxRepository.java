package com.sparsh.sentinel.ingest.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims the oldest unpublished events.
     *
     * <p>{@code SKIP LOCKED} lets a second relay instance take the next batch instead of waiting
     * on this one's locks - and, more to the point, stops it publishing the same rows twice.
     */
    @Query(value = """
            SELECT * FROM sentinel.outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}

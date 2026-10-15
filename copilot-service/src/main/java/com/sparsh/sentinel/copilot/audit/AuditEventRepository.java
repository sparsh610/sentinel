package com.sparsh.sentinel.copilot.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Newest first, optionally for one actor and/or one action. */
    @Query("""
            select e from AuditEvent e
            where (:actor is null or e.actor = :actor)
              and (:action is null or e.action = :action)
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEvent> search(@Param("actor") String actor, @Param("action") String action, Pageable page);
}

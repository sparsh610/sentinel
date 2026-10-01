package com.sparsh.sentinel.copilot.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {

    List<Investigation> findByAlertIdOrderByStartedAtDesc(UUID alertId);

    /**
     * A run is a thread inside this service; if the service stops, the run stops with it and its
     * row would say RUNNING forever. Called at start-up.
     */
    @Modifying
    @Query("""
            UPDATE Investigation i
               SET i.status = com.sparsh.sentinel.copilot.agent.Investigation.Status.FAILED,
                   i.failureReason = 'Interrupted: copilot-service restarted while it ran',
                   i.finishedAt = :now
             WHERE i.status = com.sparsh.sentinel.copilot.agent.Investigation.Status.RUNNING
            """)
    int failInterrupted(@Param("now") Instant now);
}

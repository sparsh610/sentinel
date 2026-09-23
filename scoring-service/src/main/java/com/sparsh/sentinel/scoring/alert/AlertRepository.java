package com.sparsh.sentinel.scoring.alert;

import com.sparsh.sentinel.scoring.detect.Finding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /** The work queue: most serious first, newest first among equals. */
    List<Alert> findByStatusOrderByScoreDescRaisedAtDesc(Alert.Status status, Pageable page);

    long countByStatus(Alert.Status status);

    @Query("""
            SELECT count(f) > 0 FROM AlertFinding f
            WHERE f.rule = :rule
              AND f.alert.customerId = :customerId
              AND f.alert.bookedAt > :since
            """)
    boolean existsFindingForCustomerSince(@Param("customerId") String customerId,
                                          @Param("rule") Finding.Rule rule,
                                          @Param("since") Instant since);
}

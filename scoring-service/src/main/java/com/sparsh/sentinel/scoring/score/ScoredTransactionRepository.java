package com.sparsh.sentinel.scoring.score;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ScoredTransactionRepository extends JpaRepository<ScoredTransaction, UUID> {

    /**
     * Records the transaction unless it is already there.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than check-then-insert, so two consumers racing
     * on the same redelivered record cannot both see "not there yet".
     *
     * @return 1 if this call recorded it, 0 if it had been scored before
     */
    @Modifying
    @Query(value = """
            INSERT INTO sentinel.scored_transaction
                (transaction_id, customer_id, direction, channel, amount, currency,
                 counterparty_country, booked_at, scored_at)
            VALUES (:#{#tx.transactionId}, :#{#tx.customerId}, :#{#tx.direction}, :#{#tx.channel},
                    :#{#tx.amount}, :#{#tx.currency}, :#{#tx.counterpartyCountry},
                    :#{#tx.bookedAt}, :#{#tx.scoredAt})
            ON CONFLICT (transaction_id) DO NOTHING
            """, nativeQuery = true)
    int recordIfAbsent(@Param("tx") ScoredTransaction tx);

    /**
     * Cash deposits in {@code [floor, ceiling)} booked in {@code (from, to]} for one customer.
     * The upper bound is inclusive so the deposit being scored counts itself.
     */
    @Query("""
            SELECT count(t) FROM ScoredTransaction t
            WHERE t.customerId = :customerId
              AND t.channel = 'CASH'
              AND t.direction = 'CREDIT'
              AND t.amount >= :floor AND t.amount < :ceiling
              AND t.bookedAt > :from AND t.bookedAt <= :to
            """)
    long countCashDepositsInBand(@Param("customerId") String customerId,
                                 @Param("floor") BigDecimal floor,
                                 @Param("ceiling") BigDecimal ceiling,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);
}

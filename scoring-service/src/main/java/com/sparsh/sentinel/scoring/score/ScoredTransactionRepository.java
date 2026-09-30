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
                 counterparty_country, counterparty_name, booked_at, scored_at)
            VALUES (:#{#tx.transactionId}, :#{#tx.customerId}, :#{#tx.direction}, :#{#tx.channel},
                    :#{#tx.amount}, :#{#tx.currency}, :#{#tx.counterpartyCountry},
                    :#{#tx.counterpartyName}, :#{#tx.bookedAt}, :#{#tx.scoredAt})
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

    /**
     * The customer's recent activity, as the models' features need it: one row, every window
     * ending at {@code at} and including the transaction being scored - the same "this one
     * included" the training features in {@code ml/sentinel_features.py} use.
     *
     * @param counterparty the transaction's counterparty, or "" when it has none (a null would
     *                     leave Postgres unable to infer the parameter's type)
     */
    @Query(value = """
            SELECT count(*) FILTER (WHERE t.booked_at > :since24h)                           AS "count24h",
                   coalesce(sum(t.amount) FILTER (WHERE t.booked_at > :since24h), 0)          AS "sum24h",
                   count(*) FILTER (WHERE t.booked_at > :since24h AND t.direction = 'CREDIT') AS "creditCount24h",
                   count(*) FILTER (WHERE t.booked_at > :since24h AND t.channel = 'CASH')     AS "cashCount24h",
                   count(*)                                                                   AS "count7d",
                   coalesce(sum(t.amount), 0)                                                 AS "sum7d",
                   (SELECT count(*)
                      FROM (SELECT min(f.booked_at) AS first_seen
                              FROM sentinel.scored_transaction f
                             WHERE f.customer_id = :customerId
                               AND f.counterparty_name IS NOT NULL
                               AND f.booked_at <= :at
                             GROUP BY f.counterparty_name) firsts
                     WHERE firsts.first_seen > :since24h)                                     AS "newCounterparties24h",
                   EXISTS (SELECT 1
                             FROM sentinel.scored_transaction p
                            WHERE p.customer_id = :customerId
                              AND p.counterparty_name = :counterparty
                              AND p.transaction_id <> :transactionId
                              AND p.booked_at <= :at)                                         AS "counterpartySeenBefore"
              FROM sentinel.scored_transaction t
             WHERE t.customer_id = :customerId
               AND t.booked_at > :since7d AND t.booked_at <= :at
            """, nativeQuery = true)
    CustomerWindow customerWindow(@Param("customerId") String customerId,
                                  @Param("transactionId") UUID transactionId,
                                  @Param("counterparty") String counterparty,
                                  @Param("at") Instant at,
                                  @Param("since24h") Instant since24h,
                                  @Param("since7d") Instant since7d);
}

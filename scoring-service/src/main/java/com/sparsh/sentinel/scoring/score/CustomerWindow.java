package com.sparsh.sentinel.scoring.score;

import java.math.BigDecimal;

/**
 * One customer's recent activity up to and including the transaction being scored. What the
 * models' behavioural features are computed from.
 */
public interface CustomerWindow {

    long getCount24h();

    BigDecimal getSum24h();

    long getCreditCount24h();

    long getCashCount24h();

    long getCount7d();

    BigDecimal getSum7d();

    /** Counterparties whose first transaction with this customer falls in the last 24 hours. */
    long getNewCounterparties24h();

    /** Whether an earlier transaction of this customer's had the same counterparty. */
    boolean getCounterpartySeenBefore();
}

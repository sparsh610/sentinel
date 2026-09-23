-- What scoring-service has seen, and what it raised.
--
-- scoring-service owns these tables and never reads tx-ingest's. Everything it knows about a
-- transaction arrived on the event.

-- One row per transaction scored.
--
-- Two jobs. The primary key is the idempotency guard: delivery from the outbox is at least
-- once, and a redelivered event must not raise a second alert. And it is the history the
-- detectors look back over, kept here so that scoring does not need a query into another
-- service's schema to know what a customer did yesterday.
CREATE TABLE sentinel.scored_transaction
(
    transaction_id       UUID           PRIMARY KEY,
    customer_id          VARCHAR(32)    NOT NULL,
    direction            VARCHAR(8)     NOT NULL,
    channel              VARCHAR(16)    NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    counterparty_country VARCHAR(2),
    booked_at            TIMESTAMPTZ    NOT NULL,
    scored_at            TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_scored_customer_booked ON sentinel.scored_transaction (customer_id, booked_at);

-- At most one alert per transaction, carrying every finding against it.
--
-- The transaction's details are copied in rather than joined, because an alert records what
-- the detectors saw at the moment they fired. If the source transaction is later corrected,
-- the alert must still show the facts it was raised on - that is what an auditor asks for.
CREATE TABLE sentinel.alert
(
    id                   UUID           PRIMARY KEY,
    transaction_id       UUID           NOT NULL,
    customer_id          VARCHAR(32)    NOT NULL,
    customer_segment     VARCHAR(16)    NOT NULL,
    direction            VARCHAR(8)     NOT NULL,
    channel              VARCHAR(16)    NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    counterparty_name    VARCHAR(256),
    counterparty_country VARCHAR(2),
    booked_at            TIMESTAMPTZ    NOT NULL,

    -- The highest finding's score. What the queue sorts on.
    score                NUMERIC(5, 4)  NOT NULL,
    status               VARCHAR(16)    NOT NULL,
    raised_at            TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_alert_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_alert_score CHECK (score BETWEEN 0 AND 1),
    -- Only OPEN is written this week. The rest arrive with the approval flow in weeks 5-6 and
    -- are declared now so that migration does not have to rewrite this constraint.
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'ESCALATED', 'CLOSED'))
);

CREATE INDEX ix_alert_queue ON sentinel.alert (status, score DESC, raised_at DESC);
CREATE INDEX ix_alert_customer ON sentinel.alert (customer_id, booked_at);

-- Which detector fired and why. An analyst has to see the reason, not just a number.
CREATE TABLE sentinel.alert_finding
(
    id       BIGSERIAL     PRIMARY KEY,
    alert_id UUID          NOT NULL REFERENCES sentinel.alert (id) ON DELETE CASCADE,
    rule     VARCHAR(32)   NOT NULL,
    score    NUMERIC(5, 4) NOT NULL,
    reason   TEXT          NOT NULL,

    CONSTRAINT ck_finding_score CHECK (score BETWEEN 0 AND 1)
);

CREATE INDEX ix_finding_alert ON sentinel.alert_finding (alert_id);

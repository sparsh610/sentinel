-- Customers and the transactions booked against them, plus the outbox that carries each
-- transaction to Kafka.
--
-- tx-ingest owns these tables. scoring-service never reads them: everything it needs arrives
-- on the event, so the two services can change their schemas independently.

CREATE TABLE sentinel.customer
(
    -- The core-banking customer number, not a surrogate key: it is what every upstream
    -- system already uses to refer to the customer.
    id           VARCHAR(32)  PRIMARY KEY,
    full_name    VARCHAR(256) NOT NULL,

    -- Peer group. "Unusual" only means something relative to one - the KMeans model in
    -- week 4 learns finer segments; this is the one the bank assigns at onboarding.
    segment      VARCHAR(16)  NOT NULL,
    country      VARCHAR(2)   NOT NULL,
    risk_rating  VARCHAR(8)   NOT NULL,
    onboarded_on DATE         NOT NULL,

    CONSTRAINT ck_customer_segment CHECK (segment IN ('RETAIL', 'BUSINESS', 'CORPORATE')),
    CONSTRAINT ck_customer_risk CHECK (risk_rating IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE sentinel.transaction
(
    id                   UUID           PRIMARY KEY,

    -- The source system's own reference. Unique, because a payment file that is delivered
    -- twice must not book every payment twice.
    external_ref         VARCHAR(64)    NOT NULL,
    customer_id          VARCHAR(32)    NOT NULL REFERENCES sentinel.customer (id),
    direction            VARCHAR(8)     NOT NULL,
    channel              VARCHAR(16)    NOT NULL,

    -- NUMERIC, never a float: 0.1 + 0.2 has to equal 0.3 in a ledger.
    amount               NUMERIC(19, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    counterparty_name    VARCHAR(256),
    counterparty_country VARCHAR(2),

    -- When the bank booked it, versus when Sentinel received it. They differ whenever a file
    -- is replayed, and every time-window rule must use the first.
    booked_at            TIMESTAMPTZ    NOT NULL,
    received_at          TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_transaction_external_ref UNIQUE (external_ref),
    CONSTRAINT ck_transaction_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_transaction_channel CHECK (channel IN ('CARD', 'TRANSFER', 'CASH')),
    CONSTRAINT ck_transaction_amount CHECK (amount > 0)
);

CREATE INDEX ix_transaction_customer_booked ON sentinel.transaction (customer_id, booked_at DESC);

-- Transactional outbox.
--
-- A transaction and its event are written in the same database transaction, and a relay
-- publishes the event afterwards. Publishing to Kafka from the request handler cannot be made
-- atomic with the insert: a crash between the two either loses the event - a transaction that
-- is never scored - or publishes one for a row that was rolled back.
CREATE TABLE sentinel.outbox_event
(
    -- Sequence order is publish order.
    id           BIGSERIAL    PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    message_key  VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

-- The relay only ever asks for unpublished rows. A partial index stays small however long the
-- published history grows.
CREATE INDEX ix_outbox_unpublished ON sentinel.outbox_event (id) WHERE published_at IS NULL;

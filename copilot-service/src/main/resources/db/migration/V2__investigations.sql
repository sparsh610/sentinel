-- Investigations: the agent's work on one alert, and the people who decided it.
--
-- Operational data, so it lives in `sentinel` next to the alerts, not in `knowledge` with the
-- policy corpus - a re-index of the documents must never touch a case. copilot-service still
-- keeps its Flyway history in `knowledge`; these tables are named with their schema.
--
-- alert_id is not a foreign key: the alert table belongs to scoring-service, which owns its
-- lifecycle. The investigation keeps a snapshot of what it was raised on instead.

CREATE TABLE sentinel.investigation
(
    id             UUID           PRIMARY KEY,
    alert_id       UUID           NOT NULL,
    transaction_id UUID           NOT NULL,
    customer_id    VARCHAR(32)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    planner        VARCHAR(8)     NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    case_note      TEXT,
    -- MODEL when the local model wrote the note, TEMPLATE when it was unavailable.
    note_source    VARCHAR(8),
    failure_reason TEXT,
    started_at     TIMESTAMPTZ    NOT NULL,
    finished_at    TIMESTAMPTZ,

    CONSTRAINT ck_investigation_status CHECK (status IN
        ('RUNNING', 'FAILED', 'DRAFTED', 'AWAITING_SIGN_OFF', 'REPORTED', 'CLOSED')),
    CONSTRAINT ck_investigation_note_source CHECK (note_source IN ('MODEL', 'TEMPLATE'))
);

CREATE INDEX ix_investigation_alert ON sentinel.investigation (alert_id, started_at DESC);

-- The execution trace: every tool call, in order, with what went in and what came out.
-- An investigation that cannot be explained cannot be approved.
CREATE TABLE sentinel.investigation_step
(
    id               BIGSERIAL   PRIMARY KEY,
    investigation_id UUID        NOT NULL REFERENCES sentinel.investigation (id) ON DELETE CASCADE,
    step_no          INT         NOT NULL,
    tool             VARCHAR(32) NOT NULL,
    input            TEXT        NOT NULL,
    output           TEXT,
    status           VARCHAR(8)  NOT NULL,
    duration_ms      INT         NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_investigation_step UNIQUE (investigation_id, step_no),
    CONSTRAINT ck_step_status CHECK (status IN ('OK', 'ERROR', 'REFUSED'))
);

-- Every human decision, appended, never updated: who, in which role, did what, and why.
CREATE TABLE sentinel.investigation_decision
(
    id               BIGSERIAL    PRIMARY KEY,
    investigation_id UUID         NOT NULL REFERENCES sentinel.investigation (id) ON DELETE CASCADE,
    actor            VARCHAR(64)  NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    action           VARCHAR(16)  NOT NULL,
    comment          TEXT,
    decided_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_decision_role CHECK (role IN ('ANALYST', 'SENIOR_APPROVER')),
    CONSTRAINT ck_decision_action CHECK (action IN ('ESCALATE', 'CLOSE', 'APPROVE', 'RETURN'))
);

CREATE INDEX ix_decision_investigation ON sentinel.investigation_decision (investigation_id, decided_at);

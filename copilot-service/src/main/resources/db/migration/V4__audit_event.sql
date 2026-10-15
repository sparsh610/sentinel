-- The audit log: who did what, through which door, and whether it was allowed (week 7).
--
-- Every decision, document upload, policy question, MCP tool call, agent run and refused request
-- is one row. The execution trace says what the agent did inside one investigation; this says
-- what every person and client did across all of them - and it is the record a supervisor or an
-- auditor asks for, so the database itself refuses to change it.

CREATE TABLE sentinel.audit_event
(
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    -- The token's preferred_username, or "agent for <name>" on the agent's background thread.
    actor       VARCHAR(128) NOT NULL,
    -- API: the console or any REST caller. MCP: an MCP client such as Claude Code. AGENT: the
    -- investigation agent working for the person who started it.
    channel     VARCHAR(8)   NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    -- What it was done to: an alert, investigation or document id, or a path.
    target      VARCHAR(128),
    outcome     VARCHAR(8)   NOT NULL,
    -- Personal data is masked before it is written here, as it is before it reaches a model.
    detail      TEXT,

    CONSTRAINT ck_audit_channel CHECK (channel IN ('API', 'MCP', 'AGENT')),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('OK', 'DENIED', 'FAILED'))
);

CREATE INDEX ix_audit_occurred ON sentinel.audit_event (occurred_at DESC);
CREATE INDEX ix_audit_actor ON sentinel.audit_event (actor, occurred_at DESC);

-- Append-only. The application only ever inserts; a row that could be edited afterwards would
-- not be evidence of anything. The service's database role could still drop the trigger - in a
-- bank the log goes to a store that role cannot write to at all - but nothing in Sentinel can
-- rewrite history by accident or through an API.
CREATE FUNCTION sentinel.audit_event_is_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'sentinel.audit_event is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER audit_event_no_update_or_delete
    BEFORE UPDATE OR DELETE ON sentinel.audit_event
    FOR EACH ROW EXECUTE FUNCTION sentinel.audit_event_is_append_only();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON sentinel.audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION sentinel.audit_event_is_append_only();

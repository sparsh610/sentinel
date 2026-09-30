-- The models need to know whether a customer has paid or been paid by this counterparty
-- before, and how many new ones they met in the last day - fan-out and fan-in are what most
-- laundering typologies look like from one account. That needs the counterparty in the ledger.
--
-- Nullable: cash has no counterparty, and rows scored before this migration never recorded one.
-- For those old rows a known counterparty looks new the first time it comes back - an extra
-- signal once, never a missed one.
ALTER TABLE sentinel.scored_transaction
    ADD COLUMN counterparty_name VARCHAR(256);

CREATE INDEX ix_scored_customer_counterparty
    ON sentinel.scored_transaction (customer_id, counterparty_name, booked_at)
    WHERE counterparty_name IS NOT NULL;

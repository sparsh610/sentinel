-- Who started each investigation, and which policy documents they may see (week 7).
--
-- The agent runs on a background thread, after the request that started it has gone, so it
-- cannot ask the token who it is working for. It reads this instead: the policy lookup applies
-- the starter's clearance, and the audit log names them. Decisions were already attributed;
-- from week 7 the name comes from the token rather than a field anyone could type into.
--
-- Rows from before week 7 have no starter. Their clearance is the analyst default, which is
-- what every run used until document ACLs existed.

ALTER TABLE sentinel.investigation
    ADD COLUMN started_by VARCHAR(64),
    ADD COLUMN clearance  VARCHAR(64) NOT NULL DEFAULT 'PUBLIC,INTERNAL';

ALTER TABLE sentinel.investigation
    ALTER COLUMN clearance DROP DEFAULT;

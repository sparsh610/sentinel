-- The case note as the model wrote it, with personal data still masked (week 7).
--
-- The model never sees counterparty names, IBANs, card numbers or e-mail addresses - only stable
-- tokens such as PARTY-3f9a1c2e - so what it writes contains the tokens. case_note is that text
-- with the names put back, for the analyst; this column keeps the masked original, which is what
-- an MCP client's hosted model is shown. Notes drafted before week 7 have no masked copy.

ALTER TABLE sentinel.investigation
    ADD COLUMN case_note_masked TEXT;

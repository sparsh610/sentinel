-- Metadata about the policy documents that have been ingested.
--
-- The chunks and their embeddings live in knowledge.document_chunk, which the Spring AI
-- pgvector store owns and creates. This table is what the application knows about a document
-- as a whole: who may see it, what it was called, and whether we have ingested it before.

CREATE TABLE knowledge.document
(
    id             UUID         PRIMARY KEY,
    title          VARCHAR(512) NOT NULL,
    filename       VARCHAR(512) NOT NULL,
    content_type   VARCHAR(255) NOT NULL,
    size_bytes     BIGINT       NOT NULL,

    -- Enforced at retrieval time from week 7. Present from the start so that no document is
    -- ever ingested without one - backfilling a classification later is how confidential
    -- material leaks into answers.
    classification VARCHAR(32)  NOT NULL,

    chunk_count    INTEGER      NOT NULL,

    -- SHA-256 of the uploaded bytes. Re-uploading the same file would otherwise duplicate
    -- every chunk and quietly skew retrieval towards it.
    -- varchar, not char: Postgres bpchar carries blank-padding semantics and no performance
    -- benefit, and Hibernate maps a String of length 64 to varchar(64).
    checksum       VARCHAR(64)  NOT NULL,

    uploaded_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_document_checksum UNIQUE (checksum),
    CONSTRAINT ck_document_classification
        CHECK (classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL'))
);

COMMENT ON TABLE knowledge.document IS
    'Ingested policy documents. Chunks and embeddings are in knowledge.document_chunk.';

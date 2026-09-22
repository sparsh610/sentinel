-- Runs once, on first start of an empty Postgres volume.
-- pgvector backs the policy-document store used for retrieval.
CREATE EXTENSION IF NOT EXISTS vector;

-- Schemas: keep the operational data and the vector store apart, so a re-index
-- of the documents can never touch transactions, cases or the audit log.
CREATE SCHEMA IF NOT EXISTS sentinel;   -- customers, transactions, alerts, cases, audit
CREATE SCHEMA IF NOT EXISTS knowledge;  -- policy documents and their embeddings

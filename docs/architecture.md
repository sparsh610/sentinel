# Architecture

## The flow, end to end

1. **Ingest.** `tx-ingest` accepts a transaction (REST) or replays a file of them, writes it to
   Postgres and publishes it to the `transactions` Kafka topic.
2. **Score.** `scoring-service` consumes the topic. For each transaction it runs three rules and
   three ONNX models — the supervised classifier, and the Isolation Forest judged within the
   peer segment KMeans assigns. If any of them fires it writes one **alert** carrying every
   finding.
3. **Investigate.** An analyst opens the alert in the console. The investigation agent in
   `copilot-service` runs its tools: pull the customer's recent transactions, place the
   transaction against its peer segment, retrieve the policy clause that applies, and draft a
   case note citing it.
4. **Decide.** The analyst reads the draft and the execution trace, then approves or rejects.
   Above a value threshold a senior approver signs off. The decision, the evidence and the
   trace are written to the audit log.
5. **Ask.** At any point the analyst can ask the copilot a free question over the policy corpus
   ("when does a structuring pattern have to be reported?") and get an answer with citations
   that open the source paragraph.

## Services

### `tx-ingest` (:8082)
Owns the transaction and customer tables. Bursty and I/O-bound. Deliberately dumb — it does no
scoring, so that the scoring model can change without touching ingest.

A transaction and its event are written in one database transaction, to `transaction` and
`outbox_event`; a relay publishes the outbox to Kafka, keyed by customer id
([design decision §12](design-decisions.md#12-why-an-outbox-rather-than-publishing-to-kafka-directly)).
A repeated `externalRef` is a no-op, so senders can retry safely.

### `scoring-service` (:8083)
Loads the `.onnx` files produced by `ml/` at start-up and holds them in memory. CPU-bound,
steady throughput. Owns the alert table and the scoring threshold.

Every source of suspicion is a `Detector`: three rules — large cash, structuring, high-risk
jurisdiction — and two model detectors
([§13](design-decisions.md#13-why-rules-ship-before-the-models-and-stay-after),
[§14](design-decisions.md#14-why-the-models-are-trained-on-the-ibm-aml-data-and-what-they-actually-achieve)).
Without exported models the service starts anyway and scores with the rules alone. A transaction
yields at most one alert, carrying every finding against it. The `scored_transaction` ledger
makes redelivery a no-op and is the history the structuring rule looks back over. A record
that cannot be parsed goes to `transactions-dlt` instead of blocking its partition.

The **feature contract** between training and serving lives here: the feature order in
`ml/models/<model>.features.json` must match what the Java feature builder produces. A test
asserts this, and each `.onnx` file carries its own feature list, which is checked again when
the service loads it, because a silent feature-order mismatch produces plausible, wrong scores — the
worst possible failure mode.

### `copilot-service` (:8081)
Two responsibilities that share a vector store:

- **Retrieval.** Policy documents are chunked, embedded and stored in `knowledge` (pgvector).
  Queries retrieve with a document-level access filter applied, so a restricted document cannot
  reach a user who is not entitled to it.
- **The agent.** A planner decides which tools to call; the executor runs them under a step
  limit and records every step. Tools: `customerHistory`, `peerSegment`, `policyLookup`,
  `riskScore`, `draftCaseNote`.

Responses stream to the console over SSE.

### `console` (:4200)
Angular. Three screens: the alert queue, the case view (evidence, draft note, execution trace,
approve/reject), and the copilot chat. Later, the evaluation page.

## Data

Two schemas in one Postgres instance:

| Schema | Tables |
|---|---|
| `sentinel` | `customer`, `transaction`, `outbox_event` (tx-ingest) · `scored_transaction`, `alert`, `alert_finding` (scoring-service) · `investigation`, `investigation_step`, `investigation_decision` (copilot-service) · `audit_event` (week 7) |
| `knowledge` | `document`, `document_chunk` (with the `vector` column), `document_acl` |

They are separate so that re-indexing the policy corpus can never touch operational or audit
data.

Within `sentinel`, each service migrates only its own tables and keeps its own Flyway history
table (`flyway_history_tx_ingest`, `flyway_history_scoring`). Neither service reads the other's
tables: scoring learns everything from the event.

## Security model

- **Keycloak** issues tokens; the services are resource servers.
- Roles: `ANALYST` (work alerts, read non-confidential policy), `SENIOR_APPROVER` (sign off
  above threshold, read all policy), `ADMIN` (upload documents, manage ACLs).
- Document ACLs are enforced in the **retrieval filter**, not in the UI. The test that matters
  is that an `ANALYST` asking a question whose answer lives only in a confidential document gets
  "I don't have a source for that", not the answer.
- PII masking sits between the service and the model endpoint. It is applied to model-bound
  text only — tools still query the database with real identifiers.
- Every agent run, every approval and every document access writes an `audit_event`.

## Observability

Actuator health and info on every service. OpenTelemetry traces span ingest → Kafka → scoring →
alert so a single transaction can be followed end to end. Agent runs are traced as one span per
tool call, which is also what the UI renders as the execution trace.

## Deployment

Local: Docker Compose (this repo). Target: GCP Cloud Run, one service per container, Cloud SQL
for Postgres, built and deployed by GitHub Actions.

---

## Diagram

The ASCII diagram in the [README](../README.md) is the current source of truth. Replace it with
a rendered image before sharing the repo — draw it in [Excalidraw](https://excalidraw.com) or
Mermaid, export to `docs/images/architecture.png`, and embed it in both files.

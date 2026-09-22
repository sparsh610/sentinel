# Design decisions

The point of this page is to answer "why is that in there?" for every non-obvious choice in
the system. Each decision states the alternative that was rejected and why.

> Keep this honest. If something is here because it was interesting to build rather than
> because the system needed it, say so.

---

## 1. Why Kafka between ingest and scoring, instead of a REST call

**Rejected:** `tx-ingest` calls `scoring-service` over HTTP and waits for a score.

Transaction monitoring is a stream, not a request/response. Three properties make the broker
the right answer rather than decoration:

- **Back-pressure.** A payment file lands with 200,000 transactions in it. A synchronous call
  either times out or forces the ingest side to throttle itself. A log lets the scoring side
  consume at its own rate.
- **Replay.** When a model is retrained, yesterday's transactions have to be re-scored against
  it. With a log that is a consumer-group reset; with HTTP it is a bespoke batch job.
- **More than one consumer.** Scoring is the first consumer. Audit is the second, and
  case-management analytics will be the third. None of them should require ingest to know they
  exist.

**Cost, stated plainly:** it adds a broker to the local stack and makes end-to-end debugging
harder. That is why Kafka sits behind a Compose profile — the system starts without it.

## 2. Why the ML models are exported to ONNX and served from Java

**Rejected:** a Python FastAPI service holding the models, called over HTTP from Java.

- One fewer runtime in production. The scoring path stays inside the JVM, so there is no second
  service to deploy, monitor, secure and keep dependency-patched.
- **No network hop on the hot path.** Scoring is per-transaction; an in-process ONNX Runtime
  call is microseconds, an HTTP round trip is milliseconds.
- Training and serving get decoupled properly. The notebooks in `ml/` can use whatever Python
  version and libraries suit them; the contract between training and serving is the `.onnx`
  file and its feature order — nothing else.

**Cost:** not every scikit-learn or XGBoost construct converts cleanly to ONNX, and feature
engineering has to be reimplemented on the Java side or baked into the ONNX graph. Feature
parity between training and serving is the real risk here and is what the tests guard.

## 3. Why there is both a supervised and an unsupervised model

This is the core modelling decision, and it is not padding.

- The **supervised** model (XGBoost, trained on labelled data) learns the fraud that was
  *already caught*. It is accurate on known patterns and useless on anything new, because a
  pattern nobody labelled cannot appear in its training signal.
- The **unsupervised** model (Isolation Forest) does not use labels at all. It flags what is
  statistically unlike normal behaviour, which is the only way a *novel* pattern surfaces
  before a human has ever named it.
- **KMeans** segmentation does a third job: "unusual" only means anything relative to a peer
  group. A €50,000 transfer is unremarkable for a corporate account and a strong signal on a
  student account. Segment first, then judge the anomaly within the segment.

An alert carries all three signals, and the analyst sees which one fired.

**On metrics:** the Kaggle dataset is roughly 0.17% positive. Accuracy is meaningless there — a
model predicting "never fraud" scores 99.8%. The model is evaluated on **PR-AUC**, and the
decision threshold is chosen from the precision/recall curve, because the threshold is a
business decision about how many false positives the investigation team can actually work
through in a day, not a modelling one.

## 4. Why RAG, rather than fine-tuning a model on the regulations

- Regulations change, and a compliance answer must cite the clause it came from. Retrieval
  gives a **citation that opens the source paragraph**; a fine-tuned model gives a fluent
  sentence with no provenance, which is worthless in an audit.
- Document-level access control is enforceable at retrieval time. A junior analyst's query
  simply never retrieves the confidential policies. Baking documents into weights makes that
  impossible to enforce.
- Adding a new policy document is an upload, not a training run.

## 5. Why the agent cannot close a case

The investigation agent gathers evidence, retrieves the applicable rule and drafts a case note.
It **proposes**; a human analyst approves or rejects, and a senior approver signs off above a
threshold.

This is not timidity about the technology — it is what the domain requires. A suspicious-activity
determination carries regulatory and legal weight and has to be attributable to a person. The
agent's value is the twenty minutes of gathering it removes, not the decision itself.

Practical consequences in the code: every agent run has a **step limit**, every tool call is
recorded in an execution trace attached to the case, and the trace is visible in the UI.
An investigation that cannot be explained cannot be approved.

## 6. Why personal data is masked before it reaches the model

IBANs, card numbers and names are replaced with stable tokens before any prompt leaves the
service, and restored in the response where the analyst is entitled to see them.

Under GDPR the bank stays the controller of that data, and sending it to a third-party model
endpoint is a transfer that has to be justified. Masking removes the question. It also blunts
prompt injection from document content, since an injected instruction cannot reference a real
account it never saw.

**Cost:** masking degrades retrieval when the query is genuinely about a specific account, so
the masking layer is applied to model-bound text, not to the database queries the tools run.

## 7. Why three services and not seven

Ingest, scoring and copilot are separated because they have genuinely different scaling and
failure characteristics: ingest is I/O-bound and bursty, scoring is CPU-bound and steady,
the copilot is latency-bound and dependent on an external model.

Splitting further — a separate case service, a separate audit service, a separate document
service — would be architecture for its own sake at this size. They share a database schema and
a deployment, and that is the right call until something forces otherwise.

## 8. Why the agent's tools are also exposed over MCP

The investigation tools — `customerHistory`, `peerSegment`, `policyLookup`, `riskScore`,
`draftCaseNote` — are used by Sentinel's own agent *and* published from `copilot-service` as an
**MCP (Model Context Protocol) server**.

**Rejected:** keeping the tools private to the in-process agent.

- The tools are the valuable part, not the agent loop around them. An analyst who prefers to
  work from a different client should be able to reach the same tools without Sentinel
  reimplementing that client.
- It forces the tool layer to be honest. A tool can only be published over a standard protocol
  if it has a typed schema, a clear description, and no hidden coupling to the caller's state —
  which is the discipline the internal agent benefits from anyway.
- The adapter is thin. The tool implementations do not change; MCP is a transport in front of
  them.

**This does not widen the trust boundary.** The MCP endpoint sits behind the same OIDC
authentication and the same role checks as the REST API: retrieval still applies the document
ACL filter, PII masking still applies to anything model-bound, and every call still writes an
`audit_event`. Most importantly, **no tool exposed over MCP can approve or close a case.**
`draftCaseNote` produces a draft, exactly as it does for the internal agent — the approval path
stays in the console, behind a human (see §5).

**Cost:** a second entry point into the tool layer, which means authorisation has to be enforced
in the tools themselves rather than at the controller. That is the correct place for it anyway,
but it is a real constraint to hold on to as tools are added.

## 9. Why Java 21 when JDK 25 is installed

The build targets 21 (`maven.compiler.release`). It is the LTS that current enterprise job
descriptions ask for, and targeting it keeps the project buildable on any JDK from 21 upward
rather than only on the newest one.

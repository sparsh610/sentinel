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

**On metrics:** the training data is about 0.1% laundering. Accuracy is meaningless there — a
model predicting "never laundering" scores 99.9%. The model is evaluated on **PR-AUC**, and the
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
It **proposes**; a human analyst closes the case or escalates it, and an escalation becomes a
report only when a senior approver — a different person — signs it off. That follows the sample
policy the agent itself cites (AML-05.2: "An analyst may draft a report but may not submit one"),
so every escalation needs the second signature, not only those above an amount.

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

**As built (week 6).** `SentinelMcpTools` publishes seven tools over Streamable HTTP at `/mcp`:
`listOpenAlerts`, `riskScore`, `customerHistory`, `peerSegment`, `policyLookup`, `draftCaseNote`
(runs Sentinel's own agent and returns its draft) and `getInvestigation`. Each delegates to the
code the internal agent uses. Claude Code and Codex CLI connect with one command each, and the
client's own model does the reasoning — so a strong model investigates without Sentinel holding
an API key, and the client pays for its own inference.

- **Off by default.** The server starts only with the `mcp` profile. Until week 7 brings
  masking and authentication, what these tools return reaches the client's model provider, so
  the profile is for the synthetic demo data and localhost only, and says so in its config.
- **Published to the MCP server only.** The tools go to the server as MCP tool specifications,
  not as a `ToolCallbackProvider` bean: Spring AI gathers provider beans into the resolver its
  own chat models use, which would have made them callable by Sentinel's internal model too —
  and did tie the agent, the chat model and that resolver into a startup cycle. A context test
  with the real chat model guards against both.
- **Nothing that decides.** A test fails if a published tool's name suggests approving,
  escalating, closing, reporting or signing off. Identifiers coming from the model are parsed
  before any call is made.
- **Every call is logged** with its arguments; week 7 turns those lines into `audit_event` rows.


## 9. Why the model runs locally by default

Ollama is the default and a hosted endpoint is opt-in (the `cloud` profile), rather than the
other way round. That ordering is the decision, and it is a compliance one.

**What leaves the machine when the `cloud` profile is on:** the analyst's question and the two
or three excerpts retrieval selected. **What never leaves:** the corpus, because embeddings run
locally. Embedding covers every document; chat sees only what was retrieved. That is why
`spring.ai.model.chat` and `spring.ai.model.embedding` are configured separately instead of
picking one provider for both.

That split reduces exposure. It does not remove it. Retrieved excerpts are real policy text,
and the question leaks investigative context by itself - "is this structuring pattern
reportable" says what is being investigated. From week 5 the agent's tools return customer
history, and then names, IBANs and amounts would be in scope too.

In a bank that is a GDPR Article 44 transfer question and ICT third-party risk under DORA,
not a preference. Assume a provider logs prompts unless a contract says otherwise. The
production shapes are self-hosted inference inside the bank's own tenancy, or a contracted
provider with a data processing agreement, zero retention and in-region processing.

**The control that makes remote inference defensible, and the reason `classification` is
stamped on every chunk at ingestion:** route on it. `CONFIDENTIAL` chunks force local
inference; `PUBLIC` and `INTERNAL` may go remote. The copilot degrades to the local model
rather than leaking. Combined with the PII masking in §6 that is a defensible posture.

Not built yet. The `cloud` profile today is a convenience for demos against the synthetic
corpus, and it says so in a warning at the top of `application-cloud.yml`.

## 10. Why both GitHub Actions and a Jenkinsfile

**Actions is the CI that runs.** The repository is public, so CI has to execute on every push
with no infrastructure to maintain and report a badge anyone can see. Nothing about a
self-hosted controller does that better.

**The Jenkinsfile is there because the deployment target is an enterprise.** Banks and insurers
run Jenkins, and a pipeline that only exists as a vendor-specific YAML says nothing about
whether the build is portable. Expressing the same build declaratively on Jenkins is the check
that it is: same `mvn clean verify`, same JUnit publishing, same artifacts.

`MAVEN_OPTS` pins the local repository into the workspace, because a shared Jenkins agent
running concurrent builds against one `~/.m2` is a genuine and very annoying source of
corrupted-artifact failures.

**Honesty rule attached to this file:** it does not go on the resume as pipeline experience
until it has actually executed at least once (Jenkins in Docker, one green run). An untested
Jenkinsfile is a claim, not a skill.

## 11. Why Java 21 when JDK 25 is installed

The build targets 21 (`maven.compiler.release`). It is the LTS that current enterprise job
descriptions ask for, and targeting it keeps the project buildable on any JDK from 21 upward
rather than only on the newest one.

## 12. Why an outbox rather than publishing to Kafka directly

**Rejected:** `tx-ingest` saves the transaction and then calls `kafkaTemplate.send()` in the same
request.

That is two writes to two systems with no transaction spanning them. If the process dies after
the database commit and before the send, the transaction is stored and never scored — in
transaction monitoring, a silent gap. Sending first and committing second fails the other way:
an alert for a transaction that was rolled back.

So `tx-ingest` writes the transaction and an `outbox_event` row in **one database
transaction**, and a relay publishes unpublished rows afterwards. The relay claims rows with
`FOR UPDATE SKIP LOCKED`, so a second instance cannot publish the same row, and stops at the
first failed send so that a later event for a customer never overtakes an earlier one.

**The price is at-least-once delivery.** If the relay dies after the broker acknowledged a send
but before the row is marked, the event goes out again. `scoring-service` is therefore
idempotent: it records each `transactionId` in `scored_transaction` with
`ON CONFLICT DO NOTHING` before running any detector, and a redelivered event stops there.
Exactly-once across a database and Kafka is not on offer; at-least-once plus an idempotent
consumer is the standard answer.

Three smaller decisions travel with it:

- **Keyed by customer id.** Kafka orders only within a partition, and the structuring rule
  needs one customer's deposits in order.
- **No shared event class.** Each service declares its own `TransactionEvent` record, and the
  JSON is the contract. A shared jar would make the two services release together — the
  coupling the broker exists to remove. The consumer ignores unknown fields and dead-letters an
  unknown `schemaVersion`.
- **Topics are declared, never auto-created.** During the build, the dead-letter recoverer's
  default topic turned out to be `transactions-dlt`, not the `transactions.DLT` that had been
  declared, and the broker quietly created a second topic that nobody was watching. The destination is now configured
  explicitly and broker auto-creation is off, so a wrong name fails loudly.

## 13. Why rules ship before the models, and stay after

Week 3 detects with three rules — cash at or above EUR 10,000, structuring just under it, and
payments involving a FATF high-risk jurisdiction. They are not a placeholder for the models.

- **Regulators ask for specific typologies by name.** A bank has to show that it monitors for
  structuring. "The Isolation Forest would probably have caught it" is not an answer an
  examiner accepts.
- **Rules are explainable line by line.** Each finding carries a sentence naming the facts that
  fired it, which is what an analyst reads first.
- **Models cover what nobody wrote a rule for** (§3). The two are complementary. Every source of
  suspicion implements one `Detector` interface, so the models arrived in week 4 as two more
  detectors and the rules stayed.

The rule scores (0.60 / 0.75 / 0.85) are fixed weights, not probabilities. They exist so the
queue sorts on one column next to the models' scores, and they are stated honestly as such —
as is the classifier's score, which is a ranking, not a calibrated probability (§14).

**Known limit:** the structuring rule looks back from each deposit's booking time. A deposit
that arrives out of order — booked earlier than one already scored — still counts towards
later deposits but does not trigger on its own arrival. Late-arriving data is normal in
payments; handling it properly needs event-time windows, which is more machinery than this
stage justifies.

## 14. Why the models are trained on the IBM AML data, and what they actually achieve

**Rejected:** the Kaggle credit-card fraud dataset, which the original plan named. Its features
are `V1`–`V28`, the output of a PCA whose inputs were never published. `scoring-service` could
never compute them from a Sentinel transaction, so a model trained on them could be evaluated
in a notebook but never served. It would also have been card fraud, not money laundering.

**Chosen:** IBM's *Transactions for Anti-Money-Laundering* (`HI-Small`, about 5M synthetic
payments, 0.1% laundering, from Altman et al., NeurIPS 2023). Each row has a timestamp, both
accounts, an amount, a currency and a payment type — all of which map onto Sentinel's
transaction, so the features can be built identically on both sides:

- Each payment becomes two rows, a DEBIT for the payer and a CREDIT for the payee, because
  Sentinel scores from one customer's side. Cash/card/ACH/wire/cheque map to Sentinel's three
  channels; Bitcoin and self-transfers ("reinvestment") have no equivalent and are dropped.
- The 14 features (`ml/sentinel_features.py`, mirrored by `ModelFeatures.java`) are the
  transaction itself plus the customer's last 24 hours and 7 days, as the `scored_transaction`
  ledger can answer them in one query. The split is by time, never random.
- Two of them — "first transaction with this counterparty" and "new counterparties in the last
  24 hours" — took the classifier's PR-AUC from **0.04 to 0.31**. Laundering in this data is a
  graph pattern (fan-out, fan-in, chains), and those are the parts of the graph one account's
  ledger can see. They are why the ledger gained a `counterparty_name` column (migration V2).

**What the models achieve, on the later days they never saw:**

| Model | Result | What it means |
|---|---|---|
| XGBoost | PR-AUC **0.31** (random: 0.0016) | At the 0.98 threshold, about 1 alert in 3 is laundering and 38% of it is caught |
| Isolation Forest | PR-AUC **0.002** | Barely above random against the labels |
| KMeans (k = 4) | 4 segments: cash, transfers, cards, and very high-volume accounts | Each at its training data's most unusual 0.1%: per-segment thresholds flagged 585 of 1M test rows and caught 2 laundering; one global threshold flagged 20 and caught none |

**Known limits, stated rather than hidden:**

- **The Isolation Forest adds little on this data.** IBM's laundering is not statistically
  *unusual* on these features — it looks like ordinary transfers to new counterparties, which
  only a labelled model learns. It is kept because its job is the pattern nobody labelled yet,
  which no labelled test set can measure, and it carries a low fixed score (0.40) so it ranks
  below every rule.
- **The classifier's score is not a probability.** Training weighted the rare laundering rows
  about a thousand times up, which pushes outputs towards 1. It ranks; it is not calibrated.
  The analyst's text says "model score", never a percentage.
- **Domain shift.** The model learned "first payment to a new counterparty" as its strongest
  signal. In Sentinel's simulated traffic, a retail customer's first payment to IKEA looks the
  same, so the demo shows classifier alerts that an analyst would close. A bank would retrain
  on its own labelled cases; this project cannot.
- **Two copies of one query.** Each model detector builds its own features, so a transaction
  costs the ledger query twice. Sharing it would couple the detectors or add a per-transaction
  cache; at this volume the query is the cheaper option.
- **Feature parity is tested on names and arithmetic, not on the SQL.** `ModelFeaturesTest`
  checks the order against the committed manifests and each feature's formula; the window
  query itself runs against Postgres only when the service does. A Testcontainers test for it
  belongs with week 8's.

## 15. Why the agent follows a fixed plan by default, on a small local model

**Rejected:** letting a hosted model (Claude, GPT) plan the investigation by calling tools, and
running a large local model (8B+) for it.

- **The prompt carries customer data from here on.** Masking it before it may leave the machine
  is week 7 (§6). Until then a hosted model is not an option — the `cloud` profile's chat model
  is never given an investigation: the note falls back to a template and the planner to the fixed
  plan whenever the configured chat model is not the local Ollama one (`CaseNoteWriter`).
- **It has to run on the laptop it is demonstrated on.** `qwen2.5:3b` (1.9 GB) fits beside
  Postgres, Kafka and three JVMs; an 8B model does not. It is unloaded a minute after use.
- **A fixed plan is what an investigation playbook is.** The same evidence in the same order is
  predictable, auditable and costs nothing. The model's job is the part that needs language:
  turning the evidence into a note an analyst can read.

**The LLM planner is still there** (`SENTINEL_AGENT_PLANNER=LLM`). The model proposes tool calls;
Spring AI's own loop is switched off and the agent's loop makes them, through the same recorder
as the fixed plan — so the step limit is enforced around the tools, not trusted to the model. The
tools it sees take no identifiers; they are bound to the alert's customer and transaction, so no
prompt, including text injected into a policy excerpt, can point it at another customer. A 3B
model often calls only some tools; whatever it leaves out is filled in by the fixed plan and
marked "model did not ask" in the trace.

**What the trace and lifecycle guarantee:**

- Every tool call — successful, failed, or refused by the step limit — is a row in
  `investigation_step`, committed as it happens, with input, output and duration. The last step
  is reserved for the note, so evidence-gathering can never crowd it out.
- The agent stops at DRAFTED. ESCALATE and CLOSE are the analyst's; APPROVE and RETURN are the
  senior approver's, and the person who escalated cannot sign off. Each decision is appended to
  `investigation_decision`, and the alert's status in scoring-service moves with it (IN_REVIEW →
  CLOSED, or → ESCALATED on sign-off).

**Known limits:**

- **Who decides is taken from the request** until week 7 brings authentication; the four-eyes
  rule already holds, the role check does not yet.
- **A 3B model writes a serviceable draft, not a reliable one.** In testing it got the amount
  right and cited the right clauses once the clause numbers were named in its input, but it can
  still overstate ("a history of structuring"). It is a draft, read by the analyst before any
  decision — which is the design, not a workaround. CPU-only, a note takes about a minute.
- **Runs are threads in copilot-service.** A restart interrupts them; they are marked FAILED at
  start-up rather than left RUNNING, and can be run again from the console.

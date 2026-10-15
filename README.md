# Sentinel

**An AI co-pilot for transaction monitoring and compliance investigation.**

A transaction stream comes in. Machine-learning models flag suspicious activity. An agent
investigates each alert — pulls the customer's history, looks up the relevant regulation,
drafts a case note with citations — and a human analyst approves or rejects it in an Angular
console. Every step is audited.

> 🚧 **In active development.** Built part-time; see [`docs/roadmap.md`](docs/roadmap.md) for what
> is done and what is next.

<!-- TODO before sharing this repo with anyone:
     1. Architecture diagram here (docs/architecture.md has the source).
     2. Demo GIF here - alert queue -> case view -> agent investigating -> analyst approves.
     3. CI badge once the GitHub Actions workflow runs green.
     A reviewer spends about 90 seconds on this page. These three are the 90 seconds. -->

---

## Why it exists

Compliance analysts in a bank do two things all day: decide whether a flagged transaction is
actually suspicious, and justify that decision against a specific regulation. The first is a
machine-learning problem, the second is a retrieval problem, and the tedious part in between —
gathering the customer's history and drafting the case note — is what an agent can do.

Sentinel puts all three in one system, and keeps a human as the approver.

## Architecture

```
                    ┌──────────────┐
  transactions ───► │  tx-ingest   │ ──── Kafka ────┐
                    │    :8082     │                │
                    └──────┬───────┘                ▼
                           │              ┌──────────────────┐
                           │              │ scoring-service  │
                           │              │      :8083       │
                           │              │  ONNX models:    │
                           │              │  · XGBoost       │ supervised
                           │              │  · IsolationF.   │ unsupervised
                           │              │  · KMeans        │ segmentation
                           │              └────────┬─────────┘
                           │                       │ alerts
                           ▼                       ▼
                    ┌───────────────────────────────────────┐
                    │      PostgreSQL + pgvector            │
                    │  sentinel: customers, transactions,   │
                    │            alerts, cases, audit       │
                    │  knowledge: policy docs + embeddings  │
                    └───────────────────┬───────────────────┘
                                        │
                              ┌─────────┴──────────┐
                              │  copilot-service   │
                              │       :8081        │
                              │  · RAG + citations │
                              │  · investigation   │
                              │    agent + tools   │
                              │  · PII masking     │
                              │  · audit log       │
                              │  · MCP server      │
                              └─────────┬──────────┘
                                        │ SSE
                              ┌─────────┴──────────┐
                              │   console (:4200)  │
                              │  Angular analyst   │
                              │  queue · case · chat│
                              └────────────────────┘

  Keycloak (:8080) issues every token; all three services check it on every request.
```

Full detail in [`docs/architecture.md`](docs/architecture.md).

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring AI, JPA / Hibernate |
| Streaming | Apache Kafka |
| Data | PostgreSQL 16 + pgvector |
| ML | XGBoost, Isolation Forest, KMeans — trained in Python, exported to ONNX, **served from Java** via ONNX Runtime |
| LLM | Ollama (local) or any OpenAI-compatible endpoint — Hugging Face, Groq, Together, vLLM |
| Tool protocol | MCP (Model Context Protocol) — the investigation tools are published as an MCP server |
| Frontend | Angular 17+, TypeScript |
| Auth | Keycloak (OIDC), role-based access control |
| Ops | Docker Compose, GitHub Actions (primary CI) + Jenkins (`Jenkinsfile`), Testcontainers, GCP Cloud Run |

## Running it

**Prerequisites:** JDK 21+, Maven 3.9+, Docker, Node 20+.

```bash
# 1. Infrastructure: Postgres + pgvector, Kafka for the transaction stream, Keycloak for sign-in
docker compose --profile stream --profile auth up -d

# 2. Local LLM, optional but needed for the copilot
docker compose --profile llm up -d
docker exec sentinel-ollama ollama pull nomic-embed-text
docker exec sentinel-ollama ollama pull llama3.1:8b
docker exec sentinel-ollama ollama pull qwen2.5:3b

# 3. Build and test everything (the tests need none of the containers)
mvn clean test

# 4. Run the services (one terminal each)
mvn -pl tx-ingest spring-boot:run
mvn -pl scoring-service spring-boot:run
mvn -pl copilot-service spring-boot:run

# 5. Console
cd console && npm install && npm start
```

| Service | URL |
|---|---|
| copilot-service | http://localhost:8081/actuator/health |
| tx-ingest | http://localhost:8082/actuator/health |
| scoring-service | http://localhost:8083/actuator/health |
| console | http://localhost:4200 |
| Keycloak | http://localhost:8080 (admin console: `admin` / `admin`) |

### Signing in

Every request to every service needs a Keycloak token. The console signs you in on first load.
The realm in [`db/keycloak/sentinel-realm.json`](db/keycloak/sentinel-realm.json) is imported on
start and has three demo users:

| User / password | Roles | May |
|---|---|---|
| `alex.analyst` / `alex` | ANALYST | investigate, close or escalate; read PUBLIC and INTERNAL policy |
| `bea.analyst` / `bea` | ANALYST | the same - a second analyst |
| `sam.senior` / `sam` | ANALYST, SENIOR_APPROVER | also sign off escalations (never their own), read CONFIDENTIAL policy, upload policy, read the audit log |

For curl, or an MCP client, get a token from the command line (valid 8 hours):

```bash
export SENTINEL_TOKEN=$(scripts/sentinel-token.sh alex.analyst alex)
```

Service to service, copilot-service calls scoring-service and tx-ingest with its own service
account (client credentials, role `SERVICE`), so the agent works on its background thread.

## The alert pipeline

`tx-ingest` accepts transactions, `scoring-service` scores them off Kafka, and the **Alerts** screen
in the console shows the queue. The quickest way to see it work is the simulator, which sends a
batch of ordinary synthetic traffic with three known typologies planted in it:

```bash
AUTH="Authorization: Bearer $SENTINEL_TOKEN"
curl -X POST -H "$AUTH" "http://localhost:8082/api/simulations?ordinary=40"
curl -H "$AUTH" http://localhost:8083/api/alerts
```

Or press **Simulate traffic** in the console. A single transaction is booked by a source
system, not a person - the `sentinel-feeder` service account, role `INGEST`:

```bash
FEED=$(curl -s -d grant_type=client_credentials -d client_id=sentinel-feeder \
       -d client_secret=sentinel-feeder-secret \
       http://localhost:8080/realms/sentinel/protocol/openid-connect/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl -X POST http://localhost:8082/api/transactions -H "Authorization: Bearer $FEED" \
     -H "Content-Type: application/json" \
     -d '{"externalRef":"BANK-0001","customerId":"C-10002","direction":"CREDIT","channel":"CASH",
          "amount":15000,"currency":"EUR","bookedAt":"2026-09-23T10:00:00Z"}'
```

| Endpoint | Purpose |
|---|---|
| `POST :8082/api/transactions` | Accept one transaction. `201` when new, `200` when that `externalRef` was seen before — so a sender can retry safely |
| `GET :8082/api/customers/{id}/transactions` | A customer's recent transactions |
| `POST :8082/api/simulations` | Demo traffic with planted typologies |
| `GET :8083/api/alerts` | The open queue, most serious first, each alert with the findings that raised it |
| `POST :8081/api/investigations` | Start the investigation agent on an alert (`{"alertId": …}`); returns at once, the agent runs in the background |
| `GET :8081/api/investigations/{id}` | The case: execution trace, drafted note, decisions |
| `POST :8081/api/investigations/{id}/decision` | Analyst: `ESCALATE` or `CLOSE`, in the name on the token |
| `POST :8081/api/investigations/{id}/sign-off` | Senior approver (a different person): `APPROVE` or `RETURN` |
| `GET :8081/api/audit` | Senior approver: the audit log, newest first (`?actor=`, `?action=`, `?limit=`) |

Detection is three rules — cash at or above EUR 10,000, **structuring** (three or more cash
deposits just under that within 24 hours), and any payment involving a FATF high-risk
jurisdiction — plus two model detectors behind the same interface: an XGBoost classifier
trained on labelled laundering, and an Isolation Forest judged within KMeans peer segments.
[`docs/design-decisions.md`](docs/design-decisions.md) §13 explains why the rules stay, and §14
what the models achieve and where they fall short. The customers in `db/demo` are synthetic.

The models are build output, not source: train them once with the notebooks in
[`ml/`](ml/README.md) (about 15 minutes). Until then scoring-service starts with the rules
alone and says so in its log.

### Investigating an alert

Expand an alert and press **Investigate**. The agent reads the alert, the customer's history,
the transaction's peer segment and the policy that applies, then drafts a case note — each step
shown in the execution trace as it runs. The analyst closes the case or escalates it; an
escalation is reported only when a second person signs it off.

The note is written by a small local model:

```bash
docker exec sentinel-ollama ollama pull qwen2.5:3b
```

Without it the investigation still completes, with a template note built from the same
evidence. Set `SENTINEL_AGENT_PLANNER=LLM` to let the model choose the tools instead of the
fixed plan. Why it works this way: [`docs/design-decisions.md`](docs/design-decisions.md) §5 and §15.

### Investigating from Claude Code or Codex (MCP)

The same tools are published as an MCP server, so a coding-agent CLI can investigate an alert
with its own model. The client acts as the person whose token it carries: the same role checks
and document ACL apply, every call is audited in that name, and **everything it is sent is
masked** - counterparties, IBANs, card numbers and e-mail addresses arrive as stable tokens such
as `PARTY-3f9a1c2e`, because the model behind the client is a third party's.

```bash
mvn -pl copilot-service spring-boot:run -Dspring-boot.run.profiles=mcp
export SENTINEL_TOKEN=$(scripts/sentinel-token.sh alex.analyst alex)

# Claude Code
claude mcp add --transport http sentinel http://localhost:8081/mcp \
       --header "Authorization: Bearer $SENTINEL_TOKEN"
# Codex CLI (reads the token from the variable each time it connects)
codex mcp add sentinel --url http://localhost:8081/mcp --bearer-token-env-var SENTINEL_TOKEN
```

Then ask, for example: *"Using the sentinel tools, find an open alert with a high-risk
jurisdiction finding, gather the customer's history, its peer segment and the policy that
applies, and summarise the case with clause numbers."* No tool can decide a case; that stays in
the console.

## The copilot API

Ingest a policy document, then ask questions over it. There is a synthetic corpus to try in
[`docs/sample-policies/`](docs/sample-policies/).

```bash
# Uploading decides who will be shown a document, so it is for senior approvers
SENIOR=$(scripts/sentinel-token.sh sam.senior sam)
curl -H "Authorization: Bearer $SENIOR" \
     -F "file=@docs/sample-policies/internal-aml-policy.txt" \
     -F "classification=INTERNAL" \
     -F "title=Internal AML Policy" \
     http://localhost:8081/api/documents

curl -X POST http://localhost:8081/api/chat/ask \
     -H "Authorization: Bearer $SENTINEL_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"question":"When does a structuring pattern have to be reported?"}'
```

| Endpoint | Purpose |
|---|---|
| `POST /api/documents` | Upload a document (senior approver). `classification` is required — `PUBLIC`, `INTERNAL` or `CONFIDENTIAL` — and has no default on purpose |
| `GET /api/documents` | The ingested documents the caller may see |
| `POST /api/chat/ask` | Answer with citations, blocking |
| `POST /api/chat/stream` | The same, as SSE: a `citations` event first, then `token` events, then `done` |

Retrieval applies the **document ACL** inside the vector search: an analyst's question can only
match PUBLIC and INTERNAL chunks, so a CONFIDENTIAL procedure is never quoted, cited or passed
to a model for them - whether they ask in the console, over MCP, or through the agent.

Every answer is grounded in retrieved chunks and cites them inline as `[1]`, `[2]`. When
retrieval finds nothing above the similarity threshold the service answers **"I don't have a
source for that"** rather than falling back on the model's own knowledge — a compliance answer
without a traceable source is worse than no answer, because it looks right.

### Running without a local chat model

Pulling a 4.7 GB chat model is the slowest part of getting started. The `cloud` profile moves
generation to any OpenAI-compatible endpoint instead — Hugging Face's router, Groq, Together,
OpenAI, or a self-hosted vLLM. Only the 274 MB embedding model stays local.

```bash
# token from https://huggingface.co/settings/tokens
export SENTINEL_HF_TOKEN=hf_xxx
mvn -pl copilot-service spring-boot:run -Dspring-boot.run.profiles=cloud
```

Embeddings deliberately stay local even in this profile. Embedding runs over the **entire**
policy corpus; chat sees only the handful of excerpts retrieval selected. Keeping embeddings
local means the full corpus never leaves the machine. It is also why `spring.ai.model.chat`
and `spring.ai.model.embedding` are configured separately rather than picking one provider
for both.

> **This profile still sends policy text to a third party.** The corpus stays put, but the
> retrieved excerpts and the question do not — and the question leaks investigative context on
> its own. It is for demos against the synthetic corpus in `docs/sample-policies`. Do not point
> it at real internal policy, and do not use it once the agent's tools start returning customer
> data. The reasoning, and the `classification`-based routing that would make remote inference
> defensible, are in [`docs/design-decisions.md`](docs/design-decisions.md) §9.

**Local is the default for this reason** — `mvn spring-boot:run` with no profile sends nothing
anywhere.

## Troubleshooting

**`model '<name>' not found`, but `ollama list` shows it.**
You have Ollama installed natively *and* running in Docker, and something is still pointing at
port 11434. Both installs want that port and they bind different addresses — the native one
takes `127.0.0.1`, the container gets the IPv6 wildcard. `curl localhost` reaches the
container while the JVM resolves `localhost` to `127.0.0.1` and reaches the native install,
which has none of these models. Same URL, different server, and nothing in the error says so.

The Compose file therefore publishes the container on **11435**, and `SENTINEL_LLM_BASE_URL`
defaults to `http://localhost:11435`. If you see this error, something is overriding that back
to 11434 — check your `.env` and your shell environment.

```bash
# Who actually owns each port (Windows)
Get-NetTCPConnection -LocalPort 11434,11435 -State Listen |
  ForEach-Object { "$($_.LocalAddress):$($_.LocalPort) -> $((Get-Process -Id $_.OwningProcess).ProcessName)" }

# Which server is really answering, and what it has
curl -s http://localhost:11435/api/version   # the container
curl -s http://127.0.0.1:11434/api/version   # a native install, if you have one
```

A native Ollama can update itself in the background and reclaim 11434 mid-session, so this can
appear without you changing anything. If you would rather run only the native install, drop the
`llm` profile, pull the models there, and set `SENTINEL_LLM_BASE_URL=http://localhost:11434`.

**Ingestion succeeds but answers are empty or refuse everything.**
Check the similarity threshold in `sentinel.copilot.similarity-threshold`. Too high and every
chunk is discarded as noise, so the service correctly but unhelpfully refuses everything.

**A small chat model ignores the citation instructions.**
Expected. `llama3.2:1b` is convenient for a laptop demo but follows instructions poorly — it
will echo the question back or append a stray refusal. Use a larger model
(`SENTINEL_LLM_CHAT_MODEL=llama3.1:8b`) when the answer quality itself matters.

## Design decisions

The reasoning behind the shape of this system is in
[`docs/design-decisions.md`](docs/design-decisions.md) — why Kafka rather than a REST call, why
the models are exported to ONNX rather than served from a Python process, why there is both a
supervised and an unsupervised model, why ingest publishes through an outbox, why the agent
cannot close a case on its own, why personal data is masked before a model sees it, and why every
service checks the token itself.

## Repository layout

```
copilot-service/   RAG over policy documents + the investigation agent
tx-ingest/         accepts transactions, publishes them to Kafka through an outbox
scoring-service/   scores the stream with rules and ONNX models, raises alerts
console/           Angular analyst UI
ml/                Python notebooks that train the models and export ONNX
db/init/           Postgres bootstrap (pgvector extension, schemas)
db/keycloak/       the sentinel realm: roles, clients, demo users
scripts/           sentinel-token.sh - a demo user's token for curl and MCP clients
docs/              architecture, design decisions, roadmap
```

## A note on the data

The models are trained on IBM's public
[Transactions for Anti-Money-Laundering](https://www.kaggle.com/datasets/ealtman2019/ibm-transactions-for-anti-money-laundering-aml)
dataset (synthetic, labelled). It is **not** committed to this repository; see
[`ml/README.md`](ml/README.md) for how to fetch it. All customers, transactions and policy
documents in the demo are synthetic.

## Licence

MIT — see [LICENSE](LICENSE).

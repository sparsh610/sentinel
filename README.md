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
                              └─────────┬──────────┘
                                        │ SSE
                              ┌─────────┴──────────┐
                              │   console (:4200)  │
                              │  Angular analyst   │
                              │  queue · case · chat│
                              └────────────────────┘
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
# 1. Infrastructure (Postgres + pgvector)
docker compose up -d

# 2. Local LLM, optional but needed for the copilot
docker compose --profile llm up -d
docker exec sentinel-ollama ollama pull nomic-embed-text
docker exec sentinel-ollama ollama pull llama3.1:8b

# 3. Build and test everything
mvn clean test

# 4. Run a service
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

Add `--profile stream` for Kafka (week 3) and `--profile auth` for Keycloak (week 7).

## The copilot API

Ingest a policy document, then ask questions over it. There is a synthetic corpus to try in
[`docs/sample-policies/`](docs/sample-policies/).

```bash
curl -F "file=@docs/sample-policies/internal-aml-policy.txt" \
     -F "classification=INTERNAL" \
     -F "title=Internal AML Policy" \
     http://localhost:8081/api/documents

curl -X POST http://localhost:8081/api/chat/ask \
     -H "Content-Type: application/json" \
     -d '{"question":"When does a structuring pattern have to be reported?"}'
```

| Endpoint | Purpose |
|---|---|
| `POST /api/documents` | Upload a document. `classification` is required — `PUBLIC`, `INTERNAL` or `CONFIDENTIAL` — and has no default on purpose |
| `GET /api/documents` | What has been ingested |
| `POST /api/chat/ask` | Answer with citations, blocking |
| `POST /api/chat/stream` | The same, as SSE: a `citations` event first, then `token` events, then `done` |

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
You probably have Ollama installed natively *and* running in Docker. They both want port
11434, and they bind different addresses — the native one takes `127.0.0.1`, the container
gets the IPv6 wildcard. `curl localhost` may reach the container while the JVM resolves
`localhost` to `127.0.0.1` and reaches the native install, which has none of the models. Same
URL, different server.

```bash
# Who actually owns the port (Windows)
Get-NetTCPConnection -LocalPort 11434 -State Listen |
  ForEach-Object { (Get-Process -Id $_.OwningProcess).ProcessName }
```

Pick one. Either quit the native Ollama, or drop the `llm` profile and point the service at
the native install by pulling the models there instead.

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
supervised and an unsupervised model, and why the agent cannot close a case on its own.

## Repository layout

```
copilot-service/   RAG over policy documents + the investigation agent
tx-ingest/         accepts and replays transactions, publishes to Kafka
scoring-service/   loads the ONNX models, scores transactions, raises alerts
console/           Angular analyst UI
ml/                Python notebooks that train the models and export ONNX
db/init/           Postgres bootstrap (pgvector extension, schemas)
docs/              architecture, design decisions, roadmap
```

## A note on the data

The fraud models are trained on the public
[Kaggle credit-card fraud dataset](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud)
(anonymised, PCA-transformed). The dataset is **not** committed to this repository; see
[`ml/README.md`](ml/README.md) for how to fetch it. All customers, transactions and policy
documents in the demo are synthetic.

## Licence

MIT — see [LICENSE](LICENSE).

# Roadmap

Built part-time, roughly 8–10 hours a week. Each milestone ships to `main` and is demoable on
its own — nothing waits for the end.

| Week | Milestone | Status |
|---|---|---|
| 0 | Repo scaffold: modules, Compose stack, CI, docs | ✅ Done |
| 1–2 | **Retrieval.** Document upload, chunking, embeddings in pgvector, chat answers with citations. Minimal Angular chat | ✅ Done |
| 3 | **Stream.** Kafka, `tx-ingest`, transaction/alert schema, alert queue in the console. Detection is rule-based this week (large cash, structuring, high-risk jurisdiction); the models join them in week 4 | ✅ Done |
| 4 | **Models.** XGBoost + Isolation Forest + KMeans trained in `ml/`, exported to ONNX, served by `scoring-service`. **v1.0 tag** | ⬜ |
| 5–6 | **Agent.** Planner + tools, step limits, execution trace, analyst approve/reject flow | ⬜ |
| 6 | **MCP.** Publish the same tools as an MCP server from `copilot-service`; demo an external MCP client investigating a live alert. Thin adapter over existing tools — if the tools are not finished, this is not started | ⬜ |
| 7 | **Controls.** Keycloak roles, document ACLs enforced at retrieval, PII masking, audit log — enforced **in the tools**, so they cover the MCP entry point too | ⬜ |
| 8 | **Evidence.** Evaluation page — 30 golden Q&As for retrieval, PR-AUC and threshold curve for the models. Testcontainers tests, green CI badge. Run the `Jenkinsfile` once against Jenkins in Docker so it is verified, not just written | ⬜ |
| 9–10 | **Ship.** Cloud Run deploy, live demo link, write-up | ⬜ |

## Definition of done for v1.0 (week 4)

This is the version that gets linked from a resume, so it has a harder bar than "the code runs":

- [ ] `docker compose up` then `mvn clean test` works on a clean machine, first try
- [ ] README has an architecture image (not ASCII) and a demo GIF
- [ ] `docs/design-decisions.md` reflects what was actually built, including anything that was
      tried and abandoned
- [ ] At least one real test per service, not just `contextLoads`
- [ ] The commit history shows steady work, not one large drop

## If time runs out

Cut in this order: Cloud Run deploy → evaluation page → Keycloak → Kafka (fall back to a direct
call between ingest and scoring).

Never cut the README, the demo GIF, or `docker compose up` working.

## Deliberately out of scope

- **Fine-tuning / LoRA.** Retrieval is the right tool for cited regulatory answers
  (`docs/design-decisions.md` §4). Adding a fine-tune would be for its own sake.
- **Kubernetes.** Cloud Run covers the deployment story at this size.
- **A real payment-network integration.** Synthetic and public data only.

# console — Angular analyst UI

Not generated yet. This is the first thing to do in week 1, after the copilot service answers
its first question.

```bash
# from the repo root
npx @angular/cli@latest new console --directory console --routing --style=scss --ssr=false
```

> The Angular CLI installed on this machine is 17.3.13 and Node is 24. Angular 17 was built
> against Node 18/20, so either bump the CLI (`npx @angular/cli@latest`, as above) or pin Node
> to 20 for this folder. Generating with the latest CLI is the simpler path and the resume says
> "Angular 17+" either way.

## Screens

| Screen | Week | What it shows |
|---|---|---|
| Copilot chat | 1–2 | Streaming answers over the policy corpus, each with citations that open the source paragraph |
| Alert queue | 3 | Open alerts, which signal fired (supervised / anomaly / segment), sortable by score |
| Case view | 5–6 | Evidence, the agent's draft note, the execution trace, approve / reject |
| Evaluation | 8 | Retrieval accuracy over the 30 golden questions; PR-AUC and the threshold curve |

## Conventions

- Standalone components, signals, and the `inject()` function — not the older NgModule style.
  Worth doing deliberately: it is what current Angular job ads describe, and it is a visible
  difference from an Angular 8 codebase.
- Streaming responses arrive over SSE from `copilot-service`; keep the transport in a service,
  not in a component.
- Proxy `/api` to `localhost:8081` in development via `proxy.conf.json` rather than
  hard-coding hosts.

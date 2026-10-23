# Budgets and Subscriptions

A personal finance tracker: accounts and transactions across multiple account types, spending
insights (categories, recurring/subscription detection, big-purchase flags), multi-person running
balances (money owed by/to other people), and reminders for date-driven things like
bank-switching offers and subscription renewals.

Personal project, single real user — see `.claude/steering/product.md` for the full picture
(who it's for, goals, non-goals) and `CLAUDE.md` for how this repo is actually worked on (the
AI/human split, versioning, build order).

## Getting started

Prerequisites: Docker Desktop, a JDK (the project pins its own via the Gradle wrapper), Node.

### 1. Start Postgres

```bash
docker compose up -d
```

Starts Postgres on `:5433` and creates a `budgetsandsubscriptions_test` database alongside the
main one.

### 2. Start the backend and frontend

**Scripted** (recommended — idempotent, backgrounded, logs to `logs/`):

```bash
bash scripts/start-dev.sh          # both
bash scripts/start-dev.sh backend  # just one
bash scripts/stop-dev.sh           # stop
bash scripts/restart-dev.sh        # stop then start
```

Each script also accepts an optional `--debug` flag (backend only, attaches a JVM debugger). See
`.claude/specs/tooling_spec_001_dev_startup_scripts.md` for the full behaviour (Docker/Postgres
preflight check, safe process-kill checks, etc).

**Manual**, in two separate terminals:

```bash
cd backend && gradlew.bat bootRun   # :8090
cd frontend && npm run dev          # :5180
```

The frontend's dev server proxies `/api` to the backend automatically — open
`http://localhost:5180`.

## Ports

| Service | Port |
|---|---|
| Backend | `8090` |
| Frontend | `5180` |
| Postgres (host-mapped) | `5433` |

## Learn more

- `.claude/steering/tech.md` — full tech stack, commands, what's decided but not yet built
- `.claude/steering/structure.md` / `frontend_structure.md` — backend/frontend package layout
- `.claude/steering/frontend_conventions.md` — frontend coding conventions
- `CHANGELOG.md` — release history
- `CLAUDE.md` — how this repo is worked on (AI/human split, build order, versioning)

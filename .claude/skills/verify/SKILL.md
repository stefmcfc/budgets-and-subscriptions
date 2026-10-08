---
name: verify
description: Build/launch/drive recipe for verifying changes to Budgets and Subscriptions end-to-end — backend via curl/API, frontend via browser. Grows as real endpoints/components land; currently scaffold-only.
---

# Verifying this app end-to-end

**Status: scaffold only (2026-10-08).** There is no real endpoint or component to drive yet —
this skill currently only covers confirming the scaffolds boot. Extend each section as real
backend/frontend specs are implemented, the same way the reference projects' `verify` skills grew
with their apps.

## Launch (backend)

- `docker compose up -d` (from repo root) starts the dedicated Postgres container on **:5433**
  first — required before `bootRun` or `gradlew.bat test`, since `spring-boot-starter-data-jpa`
  needs a live connection even with zero entities. **Gotcha**: Docker Desktop's CLI being
  installed doesn't mean its daemon is running — `docker compose up -d` fails with a
  `dockerDesktopLinuxEngine` pipe error if it isn't; start Docker Desktop manually first.
- `cd backend && gradlew.bat bootRun` starts the Spring Boot server on **:8090**.
- Flyway isn't added to the build yet (see `.claude/steering/tech.md`) — no migrations run on
  startup until it is.
- Nothing to curl yet — no controller exists. Once one does, confirm with a request against its
  endpoint rather than just "the server started without an error."
- **Gotcha — port 8090 already in use.** `netstat -ano | findstr :8090` then
  `taskkill /PID <pid> /F`. Common if a previous `bootRun` was backgrounded and not cleanly
  stopped.
- Only `gradlew.bat` is checked in (Windows).

## Launch (frontend)

- `cd frontend && npm run dev` starts Vite on **:5180** (`strictPort: true`), proxying `/api` to
  `:8090` (`vite.config.ts`).
- Currently shows the default Vite + React starter page (no real UI yet).
- **Gotcha — stale `node_modules` after switching branches.** If tests/dev server suddenly fail
  with `Cannot find module` right after a `git checkout`/rebase, run `npm ci` (or `npm install`)
  again — git doesn't touch `node_modules` on checkout.

## Drive (backend, via curl)

Nothing to smoke-test yet. Once the first endpoint exists, add its `curl` recipe here — see the
reference projects' own `verify` skills for the expected shape (one block per CRUD/area, request +
expected response).

## Drive (frontend, via browser)

1. `cd frontend && npm run dev`, then open `http://localhost:5180` — the `claude-in-chrome`
   skill's tools work well for this (navigate, screenshot, `read_console_messages`).
2. Confirm the default starter page renders without console errors. Once real components exist,
   drive through the states that matter for whatever changed: loading, error (works without the
   backend running — fetch fails naturally), empty, populated.
3. Check both light and dark `prefers-color-scheme` for any new/changed styling once theming
   exists — jsdom doesn't render CSS, so a green Vitest suite is never sufficient sign-off (see
   `.claude/steering/frontend_conventions.md`).

## Worth probing (once real endpoints exist)

- Validation: missing/blank required fields, malformed IDs.
- 404 on a non-existent resource ID, and — once auth exists — `404` (not `403`) for an `id` that
  belongs to another user, since every entity will be owner-scoped.
- Response bodies never leak internals (SQL, stack traces, file paths).

## Resetting between runs

```bash
docker compose down -v && docker compose up -d
```

Drops and recreates the local Postgres volume (both the dev and `_test` databases); restart the
backend afterward so Flyway recreates the schema from scratch, once Flyway is added. **This only
affects this project's own container** — it's deliberately not shared with
`behavioural-activation`'s, so this command can never touch that project's data.

# Budgets and Subscriptions

A personal finance tracker. The primary goal is **learning Spring Boot properly by hand-coding the core layers** — the app itself is secondary to that.

## Working Agreement — READ FIRST

This project is a deliberate split between AI and human work, and it applies **only to the backend** (`backend/`). This overrides Claude Code's normal default of implementing full features end-to-end.

**Claude writes (backend):**
- Specs, high-level overviews, and architecture notes
- Project scaffolding and boilerplate (build files, config, package structure, Java **records**, empty class skeletons)

**The human hand-codes in `backend/` — Claude must NOT write these:**
- Database interactions (entities, repositories, queries, migrations)
- Security (authentication, password storage, validation)
- API layer and API documentation
- Client/service code
- Java classes (not records)
- Tests

**Claude's role during hand-coded backend work is reviewer, not author:**
- Stop at each hand-coded step and wait for the human to write it
- Review what was written: correctness, security issues, style, missed edge cases
- Give hints and explanations when asked, not full solutions — unless explicitly asked for one
- Flag it clearly if a request would break this agreement, rather than quietly complying

If a task would normally have Claude write a backend repository, service, controller, security config, or test — stop and hand it back instead.

**Frontend (`frontend/`) is the exception: Claude has free rein to fully implement it** — components, API client code, routing, styling, and tests included. Use the `modern-web-guidance` skill whenever it applies (layout/CSS, forms, performance, framework-specific patterns, etc).

**Agents and skills exist for this split** — see "Deep-dive references" below. Use `spec-writer` before implementing a new requirement, `backend-dev` for backend scaffolding only, `frontend-dev` for frontend implementation. The `backend-helper` and `backend-code-reviewer` skills support the human through the hand-coded parts without an agent needing to be invoked — they should trigger on natural phrasing ("I'm stuck trying to...", "I've finished AC-...") without being named explicitly.

## Core Features

Full detail: `.claude/steering/product.md`. Summary: accounts/transactions across multiple account types, a shared transaction-classification engine (categories, recurring/subscription detection, big-purchase flagging), spending insights, multi-person running balances, general date-triggered reminders, and auth/admin (JWT via httpOnly cookie + refresh tokens, `USER`/`ADMIN` roles).

## Build Order

1. **Authentication first** — simplest secure version, then add complexity once understood. Goal: follow one request through the whole stack, login/registration form → database storage, understanding how sensitive data is stored, how the front end talks to the system, validation, and SQL injection protection. Scope now includes registration and the admin role (Core Feature 6), not just login.
2. Accounts and transactions (Core Feature 1)
3. Categories, the transaction classification engine, and spending summaries (Core Features 2 and 3)
4. Multi-person running balances (Core Feature 4)
5. Reminders (Core Feature 5)

Current status: monorepo scaffold only — `backend/` has the Spring Boot application class and empty `application.yaml`; `frontend/` has a bare Vite + React + TS scaffold (default starter page, no app code yet). Nothing from the build order has started.

## Repo Layout

Monorepo, matching the same split used by `behavioural-activation` and `series-recommendation`:
- `backend/` — Spring Boot app (Gradle, wrapper included — always run `gradlew.bat` on this machine), dev server on `:8090`
- `frontend/` — Vite + React app (npm), dev server on `:5180`, proxies `/api` to `:8090`

Full structure/tech detail: see Deep-dive references below — don't duplicate it here.

## Deep-dive references

Read these when working in the relevant area — don't duplicate their content here:

- `.claude/steering/product.md` — what the app does, who it's for, goals/non-goals
- `.claude/steering/tech.md` — full tech stack detail, ports, commands, what's decided-but-not-yet-added to the build
- `.claude/steering/structure.md` — backend package layout, naming conventions, where tests live, and the "Who writes what" table (the concrete package-level consequence of the Working Agreement above)
- `.claude/steering/frontend_structure.md` — frontend directory layout, built vs. target
- `.claude/steering/frontend_conventions.md` — frontend coding conventions (typing, API layer, styling, testing, a11y)
- `.claude/steering/ears_format.md` — the EARS requirement format all specs use, including the label-only Spock skeleton convention `backend-dev` follows
- `.claude/agents/` — `spec-writer`, `backend-dev` (scaffolding only), `frontend-dev` (full implementation)
- `.claude/skills/` — `ears-spec` (spec drafting), `verify` (launch/drive recipe, grows with the app), `backend-helper`, `backend-code-reviewer`
- `.claude/00-initial-setup/` — the full decision history behind everything above: `02-open-questions.md` (product/feature decisions) and `03-agents-steering-and-skills.md` (this `.claude/` setup's own design discussion)

## Notes for Claude

- This repo is a learning project before it's a product — favour explaining *why*, not just supplying *what*, even in the parts Claude does write.
- Global Java/Spring defaults (layered architecture, `Clock` injection, Spock tests, no Lombok, record-vs-class rules) apply once the relevant decisions above are made — but the working agreement above takes priority: most of those layers are the human's to write, with Claude reviewing against those conventions rather than authoring them.

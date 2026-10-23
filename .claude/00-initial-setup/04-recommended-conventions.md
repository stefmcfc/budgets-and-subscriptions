## My question to Claude:
When developing Spring Boot / backend code, what conventions would you recommend alongside these?
* README
* RUNBOOK
* CHANGELOG

## Claude's response:
Since you already have README (what and how to run), RUNBOOK (how to operate and fix) and CHANGELOG (what changed), 
the gaps are usually *why* decisions were made, *how to contribute*, and *what the contract is*.

## Documentation to add

**ADRs (Architecture Decision Records)** are the most valuable addition. A short file per significant decision, 
so future you (or the new starter at Heywood) can see why you chose X over Y.

```
docs/adr/0001-use-postgres-over-dynamodb.md
docs/adr/0002-adopt-problem-details-for-errors.md
```

```markdown
# 0002. Adopt RFC 7807 Problem Details for errors

- Status: Accepted (2026-10-23)
- Supersedes: none

## Context
Why we needed to decide this.

## Decision
What we chose.

## Consequences
What gets easier, what gets harder, what we're accepting.
```

Never edit an accepted ADR. Write a new one that supersedes it.

**OpenAPI spec** (via springdoc) is the API contract. Either commit the generated `openapi.yaml` or publish it from CI, 
so consumers have something to diff.

**CONTRIBUTING.md** covers branching, commit style, how to run tests, and the PR process. Keep it short and link to it 
from the README.

**ARCHITECTURE.md** should be one page: context diagram, main components, dependencies, data stores. Put the diagram in 
Mermaid so it lives in the repo and diffs.

**CODEOWNERS** assigns reviewers automatically. It works on both GitHub (`.github/CODEOWNERS`) and GitLab (`CODEOWNERS`).

```
*                    @team-payments
/src/main/resources/db/  @team-payments @dba-group
/docs/adr/           @tech-leads
```

## Repo and process conventions

- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `feat!:` for breaking). They make the CHANGELOG nearly 
automatable and the history searchable.
- **Semantic versioning** with annotated tags (`v1.4.2`), tying back to your earlier question. Tag the commit that 
was actually deployed.
- **PR template** with a checklist (tests, migration impact, config changes, runbook updated).
- **`.editorconfig`** plus a formatter (Spotless or Checkstyle) enforced in CI, so style is never a review topic.
- **Keep a Changelog format** for the CHANGELOG (`Added / Changed / Fixed / Removed`, with an `Unreleased` section at 
the top).

## Spring Boot code conventions

- **Config**: externalise everything via env vars or Spring config. Use `@ConfigurationProperties` with `@Validated` 
rather than scattered `@Value`, so bad config fails at startup. Keep an `application-local.yml` that works out of the 
box, and document every property in the README.
- **Errors**: use Spring's `ProblemDetail` (RFC 7807) via a single `@RestControllerAdvice`. One error shape across all 
services.
- **Database migrations**: Flyway or Liquibase, never `ddl-auto=update`. Migrations are immutable once merged; fix 
forward.
- **Testing**: unit tests for logic, `@WebMvcTest` slices for controllers, Testcontainers for anything touching a real 
DB or queue. Avoid mocking the database.
- **Layering**: controller, service, repository, with DTOs at the API boundary rather than exposing entities.
- **Package by feature** (`payments/`, `members/`) over package by layer once the service grows.

## Operability conventions (what makes the RUNBOOK real)

- **Actuator** health with separate liveness and readiness probes, and `/actuator/prometheus` or equivalent metrics.
- **Structured JSON logging** with a correlation/trace ID on every line, propagated across service calls. Define log 
levels once (what is ERROR versus WARN) so alerts mean something.
- **Standard metrics naming** and a defined set of golden signals per service (latency, traffic, errors, saturation).
- **Alerts link to the RUNBOOK** by anchor, so every alert has a matching section.
- **Graceful shutdown** (`server.shutdown=graceful`) and sensible timeouts on every outbound call.
- **Feature flags and config toggles** documented, with an owner and a removal date.

## Suggested RUNBOOK structure

Since it's the one you already have, here's what I'd check it contains:

```markdown
# Runbook: <service>
## Service overview (owner, on-call channel, dependencies, SLOs)
## Dashboards and logs (direct links)
## Deploy and rollback
## Common alerts -> cause -> fix (one subsection per alert)
## Dependency failure behaviour (what happens if X is down)
## Data recovery / replay procedures
## Contacts and escalation
```

## Suggested layout

```
.
├── README.md
├── RUNBOOK.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── CODEOWNERS
├── docs/
│   ├── ARCHITECTURE.md
│   └── adr/
├── .github/ (or .gitlab/)
│   └── pull_request_template.md
└── src/
```

If you only add two things, make them **ADRs** and **conventional commits**. They cost almost nothing per use and pay 
off the most over time. I can draft a CONTRIBUTING.md, PR template or runbook skeleton as files if you want to iterate
on any of them.

## Claude's assessment — fit for this project (2026-10-23)

The question above assumed README/RUNBOOK/CHANGELOG already existed as a baseline; in reality only
`CHANGELOG.md` does. Re-evaluated every recommendation against this project's actual shape — single
real user, local-only hosting, no CI, no team, no deployment, primary goal is learning Spring Boot
by hand-coding — rather than the team/production context most of the list assumes.

Tracked here with a checkbox per item so future sessions can see what's actually been delivered,
not just recommended. Check an item off (`- [x]`) once it's implemented, and note the PR/commit that
did it.

### Adopt now — cheap and genuinely useful here

- [ ] **README.md** — the actual gap; not even flagged by the original question since it assumed
  one already existed. (`tooling_spec_003_repo_conventions.md`)
- [ ] **Conventional Commits** — already organically close to this in recent commit messages;
  worth making explicit in `CLAUDE.md`. (`tooling_spec_003_repo_conventions.md`)
- [ ] **Annotated git tags at each release** (`git tag -a v0.5.0`) — SemVer + a dated `CHANGELOG.md`
  section already happen at each release; a tag on the actual merge commit is the missing piece.
  (`tooling_spec_003_repo_conventions.md`)
- [ ] **`server.shutdown=graceful`** — free Spring Boot property; lets in-flight requests finish
  before `stop-dev.sh`'s `taskkill` lands. (`tooling_spec_003_repo_conventions.md`)
- [ ] **`.editorconfig`** — free, keeps formatting consistent between IntelliJ and any future tool.
  (`tooling_spec_003_repo_conventions.md`)

### Worth it later — not urgent, no action yet

- [ ] **PR template** (lightweight: tests pass, CHANGELOG entry, migration if relevant) — brought
  forward into `tooling_spec_003_repo_conventions.md` ahead of the rest of this group, at the
  user's request.
- [ ] **Actuator `/health`** — would replace `tooling_spec_001`'s current hack of polling
  `/api/auth/me` as a readiness proxy. Revisit next time backend config is being touched anyway.
- [ ] **`ProblemDetail` (RFC 7807)** — real idiomatic-Spring-Boot-4.x exercise; `GlobalExceptionHandler`
  already does a bespoke version of the same job. Needs its own backend spec + hand-coding pass,
  not a drop-in.
- [ ] **`@ConfigurationProperties` + `@Validated`** over scattered `@Value` — only 3-4 properties
  exist today (`app.cookie.*`, `app.cors.*`, `app.jwt.secret`); premature to migrate for this few.
- [ ] **Package-by-feature** over package-by-layer — `structure.md` deliberately chose
  package-by-layer; revisit once accounts/transactions/categories all exist side by side.
- [ ] **ARCHITECTURE.md** (one-page Mermaid diagram) — not much to diagram yet
  (frontend → backend → Postgres). Revisit once there's more shape.

### Skip — these solve team/production problems this project doesn't have

Not tracked with checkboxes — explicitly not planned, recorded so the reasoning isn't re-litigated
if raised again later:

- **ADRs as a separate `docs/adr/` system** — already have the equivalent:
  `.claude/01-authentication/design-decisions.md` and
  `.claude/02-accounts-and-transactions/design-decisions.md` *are* ADRs, organized per build-stage
  rather than per-decision, and already read by `spec-writer`. A parallel system would duplicate,
  not improve, that.
- **RUNBOOK.md** — an on-call/production-recovery document (SLOs, escalation, alerts → fix). No
  on-call, no deployment, no alerting exists or is planned. Anything genuinely useful from this
  ("how do I fix X locally") belongs in the README instead.
- **CONTRIBUTING.md, CODEOWNERS** — single owner, no external contributors, nothing to route.
- **OpenAPI/springdoc** — the only consumer is this repo's own frontend. The one real upside (a
  free Swagger UI for manual endpoint testing during hand-coding) is low priority, not zero, but
  not "adopt now."
- **Structured JSON logging + correlation IDs, Prometheus/golden-signals metrics, alerting** — all
  solve distributed-system/multi-service observability problems. This is one backend, one
  frontend, one DB, run by one person watching one terminal.
- **Feature flags** — no deployment cadence or rollout risk to manage against.
- **Testcontainers** — would *replace* the existing docker-compose-based test-DB convention
  (`tech.md`'s `budgetsandsubscriptions_test`), not add to it. A genuine future decision point, not
  a drop-in addition.
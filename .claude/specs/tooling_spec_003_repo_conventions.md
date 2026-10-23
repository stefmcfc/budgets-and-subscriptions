# Repo Conventions: README, Commit Style, Release Tags, Graceful Shutdown, Editor Config, PR Template (Tooling)

**Status**: Not started
**Priority**: P2 — quality-of-life and documentation; nothing else depends on this existing, but
every future session/contributor (including future-Claude) benefits once it does.
**Depends on**: None.
**Area**: Tooling (repo-wide) — a new root `README.md`, `.editorconfig`,
`.github/pull_request_template.md`, two `CLAUDE.md` convention additions (Conventional Commits,
annotated release tags), and one `backend/src/main/resources/application.yaml` property
(`server.shutdown: graceful`). Per `.claude/steering/structure.md`'s "Who writes what" table, the
backend human-hand-coded restriction covers `model/`, `repository/`, `service/`, `controller/`,
`security/`, `exception/`, and `config/` *Java classes* with real content — it does not cover a
single declarative YAML property with no business logic or security decision behind it, and root
`CLAUDE.md`'s own Working Agreement explicitly lists "config" among what Claude may scaffold. Every
other deliverable here is documentation/tooling, not backend feature code. Claude implements this
spec directly once approved.

## Overview

This resolves the "Adopt now" group from `.claude/00-initial-setup/04-recommended-conventions.md`'s
assessment pass (2026-10-23), which evaluated a list of general Spring Boot/repo conventions
against this project's actual shape (single real user, local-only hosting, no CI, no team,
learning-focused) and found most of the original list solves team/production problems this project
doesn't have — but five items were cheap and genuinely useful regardless, plus one item pulled
forward from the "worth it later" group at the user's explicit request (a PR template). This spec
covers all six:

1. A root `README.md` — the actual documentation gap. Only `CHANGELOG.md` exists today; there's no
   single place that says what this app is or how to run it.
2. Conventional Commits, documented as an explicit convention in `CLAUDE.md` (already organically
   close to what recent commit messages do).
3. Annotated git tags at each release (`git tag -a v0.5.0`), documented alongside the existing
   SemVer/`CHANGELOG.md` versioning convention in `CLAUDE.md`.
4. `server.shutdown: graceful` in `application.yaml` — lets in-flight requests finish before
   `scripts/stop-dev.sh`'s `taskkill` lands, rather than racing it.
5. A root `.editorconfig`, matching this repo's actual existing indentation conventions exactly (so
   adopting it produces zero diff noise on any existing file).
6. A lightweight `.github/pull_request_template.md` — a lightweight checklist rather than the
   enterprise version (config changes, runbook updated) in the original recommendation, since this
   repo has no runbook or config-flag system to check.

**Out of scope** (tracked in `04-recommended-conventions.md`'s "Worth it later"/"Skip" groups, not
re-litigated here): Actuator `/health`, `ProblemDetail`/RFC 7807, `@ConfigurationProperties`
migration, package-by-feature reorganization, `ARCHITECTURE.md`, ADRs as a separate system,
RUNBOOK.md, CONTRIBUTING.md, CODEOWNERS, OpenAPI/springdoc, structured logging/metrics/alerting,
feature flags, Testcontainers.

## Requirement 1: `README.md`

**User story**: As anyone opening this repo for the first time (including future-Claude in a new
session), I want one file that says what this app is and how to run it, instead of having to
reconstruct that from `CLAUDE.md` and `.claude/steering/`.

### TOOLING-003-AC-01 [AUTO]: README exists with the essential sections
**Statement**: The repo root shall contain a `README.md` with, at minimum: a one-paragraph
description of what the app is (sourced from `.claude/steering/product.md`'s "What it does"), a
"Getting started" section covering both the manual run path (`docker compose up -d`, then
`gradlew.bat bootRun` / `npm run dev`) and the scripted path
(`bash scripts/start-dev.sh`/`stop-dev.sh`/`restart-dev.sh`, per `tooling_spec_001`), the local
ports (backend `8090`, frontend `5180`), and a pointer to `.claude/steering/` for anything deeper
(tech stack, architecture, conventions) rather than duplicating it.

**Rationale**: The actual gap this spec exists to close — confirmed via direct check this session
that no `README.md` exists at all, not even a stale one.

**References**: `.claude/steering/product.md`, `.claude/steering/tech.md`,
`tooling_spec_001_dev_startup_scripts.md`

**Test Case (Green)**: `README.md` exists at the repo root and renders correctly as Markdown;
manually confirm each documented command actually works as written (`docker compose up -d`,
`bash scripts/start-dev.sh`, `npm run dev`, etc.) rather than trusting the prose.

### TOOLING-003-AC-02 [AUTO]: README links out instead of duplicating steering docs
**Statement**: `README.md` shall link to `.claude/steering/tech.md`, `.claude/steering/structure.md`,
and `.claude/steering/frontend_structure.md` for stack/structure detail, and to `CHANGELOG.md` for
release history, rather than restating their content.

**Rationale**: Keeps one source of truth per topic — `.claude/steering/tech.md` already documents
"what's actually declared in `backend/build.gradle.kts` and `frontend/package.json`," and duplicating
that in the README would just create a second place to go stale.

**Test Case (Green)**: manual review — no section of the README restates stack-version detail
`tech.md` already owns.

## Requirement 2: Conventional Commits

**User story**: As the person writing (or reviewing) commit messages, I want an explicit,
documented convention rather than an informal pattern that could drift, so the `CHANGELOG.md`
stays easy to derive from commit history if that's ever automated later.

### TOOLING-003-AC-03 [AUTO]: CLAUDE.md documents the Conventional Commits convention
**Statement**: Root `CLAUDE.md` shall document that commit messages in this repo follow
[Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `docs:`,
`test:`, `refactor:`, with an optional scope like `feat(backend):`/`feat(frontend):`/`feat(tooling):`,
and `!` or a `BREAKING CHANGE:` footer for breaking changes), placed in (or near) the existing
"Versioning & Changelog defaults" section since the two conventions are related.

**Rationale**: Recent commit messages (`feat(backend): logout endpoint...`, `feat(tooling): dev
startup/stop/restart scripts...`, `chore(release): cut 0.4.0`) already follow this shape
organically — this AC makes it an explicit, documented convention rather than an implicit pattern
that could drift without anyone deciding to keep it.

**References**: root `CLAUDE.md`'s "Versioning & Changelog defaults" section

**Test Case (Green)**: manual review — `CLAUDE.md` contains the convention; spot-check the last
few real commits against it (expect zero or near-zero violations, confirming this formalizes an
existing pattern rather than imposing a new one).

## Requirement 3: Annotated release tags

**User story**: As the person looking back at release history, I want the commit that was actually
merged as a given version to be directly identifiable via `git tag`, not just inferable from
`CHANGELOG.md`'s dated sections.

### TOOLING-003-AC-04 [AUTO]: CLAUDE.md documents tagging the merge commit at each release
**Statement**: Root `CLAUDE.md`'s "Versioning & Changelog" section shall document that, once a
release-cut PR is merged to `main`, the merge commit is tagged with an annotated git tag
(`git tag -a vX.Y.Z -m "..."`, then `git push origin vX.Y.Z`), matching the version just cut in
`CHANGELOG.md`/`build.gradle.kts`/`package.json`.

**Rationale**: SemVer + a dated `CHANGELOG.md` section already happen at every release
(established this session); a tag on the actual merged commit is the one piece that's currently
missing, and costs two extra commands at a point in the workflow that's already manual and
explicit (merges already require the user's go-ahead per the existing Working Agreement).

**References**: root `CLAUDE.md`'s "Versioning & Changelog defaults" section

**Test Case (Green)**: manual review of the documented step; separately (see Summary/Verification
once implemented), retroactively tag the four existing releases (`v0.2.0` through `v0.5.0`) against
their real merge commits so the convention starts consistent with history rather than only applying
from here forward — this is a real `git tag`/`git push` action against the shared remote, requiring
the same explicit go-ahead any push already does under this repo's working agreement.

## Requirement 4: Graceful shutdown

**User story**: As the person running `scripts/stop-dev.sh` against a live backend, I want
in-flight requests to finish rather than being cut off mid-response when the process is killed.

### TOOLING-003-AC-05 [AUTO]: Backend shuts down gracefully
**Statement**: `backend/src/main/resources/application.yaml` shall set `server.shutdown: graceful`,
such that an in-flight HTTP request is allowed to complete before the embedded Tomcat server stops
accepting new work and the JVM exits.

**Rationale**: Directly relevant to `tooling_spec_001_dev_startup_scripts.md`'s `stop-dev.sh`,
which currently sends `taskkill /PID ... /T /F` — a forceful kill with no grace period. Spring
Boot's graceful shutdown handles the "stop accepting new requests, finish what's in flight, then
exit" sequence inside the JVM itself before the process actually terminates; it doesn't change
`taskkill`'s own behavior, but gives the application a chance to reach a clean stopping point
first if shutdown isn't instant.

**References**: `backend/src/main/resources/application.yaml`,
`tooling_spec_001_dev_startup_scripts.md` (`stop-dev.sh`)

**Test Case (Green)**: start the backend, issue a request designed to take a few seconds (or
confirm via Spring Boot's startup log line showing graceful shutdown is enabled), trigger
`stop-dev.sh backend` mid-request, and confirm the in-flight request completes with a normal
response rather than a connection reset.

## Requirement 5: `.editorconfig`

**User story**: As anyone editing this repo in a different editor than whoever last touched a
file, I want consistent indentation/line-ending conventions enforced by the editor itself, not by
convention alone.

### TOOLING-003-AC-06 [AUTO]: `.editorconfig` matches existing real conventions exactly
**Statement**: The repo root shall contain an `.editorconfig` file specifying, per file type: 4-space
indentation for `*.java` and `*.kts` (confirmed this session against real files —
`AuthController.java`, `build.gradle.kts`), 2-space indentation for `*.ts`/`*.tsx`/`*.css`
(confirmed against `AuthContext.tsx`) and `*.yaml`/`*.yml` (confirmed against `application.yaml`),
`charset = utf-8`, `insert_final_newline = true`, and `trim_trailing_whitespace = true` as the
`[*]` default — with `end_of_line` left for `.gitattributes` to govern (`eol=lf` for `*.sh`/
`/gradlew`, `eol=crlf` for `*.bat`, LF default otherwise) rather than duplicating/risking
contradicting it in a second config file.

**Rationale**: Settings were derived from this repo's actual files, not assumed, specifically so
adding this file produces zero diff noise on anything existing — confirmed this session by
grep-checking real indentation in `AuthController.java` (4-space), `AuthContext.tsx` (2-space),
`build.gradle.kts` (4-space), and `application.yaml` (2-space).

**References**: `.gitattributes` (existing `eol` rules this file must not contradict)

**Test Case (Green)**: open a sample of existing files (one `.java`, one `.tsx`, one `.yaml`, one
`.kts`) in an `.editorconfig`-aware editor and confirm no reformatting is suggested/applied on
save.

## Requirement 6: PR template

**User story**: As the person opening (or reviewing) a PR, I want a short, repo-specific checklist
so nothing routine gets forgotten, without copying an enterprise checklist this repo has no
corresponding process for.

### TOOLING-003-AC-07 [AUTO]: PR template exists with a repo-appropriate checklist
**Statement**: The repo shall contain `.github/pull_request_template.md` with a short checklist
covering only what this repo's actual process already requires: tests pass
(`gradlew.bat test`/`npm test` as relevant), a `CHANGELOG.md` `[Unreleased]` entry added for any
user-facing change, and a Flyway migration included if the change touches the schema. It shall
**not** include a "runbook updated" or "config/feature-flag" item, since neither exists in this
repo.

**Rationale**: The original recommendation's checklist (tests, migration impact, config changes,
runbook updated) assumes infrastructure this repo doesn't have — scoped down to what's actually
true here, matching every PR body already written this session in spirit.

**References**: root `CLAUDE.md`'s "Versioning & Changelog" section (the `[Unreleased]`-entry
convention this checklist item reinforces)

**Test Case (Green)**: open a test PR (or inspect GitHub's "preview" of the template) and confirm
the checklist renders and reflects only real, current repo process.

## Cross-references

| Reference | What it provides |
|---|---|
| `.claude/00-initial-setup/04-recommended-conventions.md` | The assessment pass this spec implements the "Adopt now" group of, plus the pulled-forward PR template item |
| `.claude/steering/product.md`, `tech.md`, `structure.md`, `frontend_structure.md` | Source content the README links to/summarizes |
| `tooling_spec_001_dev_startup_scripts.md` | `stop-dev.sh`'s `taskkill` behavior that graceful shutdown mitigates against; the scripted run path the README documents |
| `.gitattributes` | Existing `eol` rules `.editorconfig` must stay consistent with, not duplicate |
| Root `CLAUDE.md`'s "Versioning & Changelog defaults" | Where the Conventional Commits and release-tag conventions are documented |
| `backend/src/main/resources/application.yaml` | Existing config file gaining `server.shutdown: graceful` |

## Acceptance Criteria Summary

- [ ] TOOLING-003-AC-01 [AUTO]: `README.md` exists with description, getting-started (manual +
  scripted), and ports
- [ ] TOOLING-003-AC-02 [AUTO]: README links to steering docs/CHANGELOG rather than duplicating them
- [ ] TOOLING-003-AC-03 [AUTO]: `CLAUDE.md` documents the Conventional Commits convention
- [ ] TOOLING-003-AC-04 [AUTO]: `CLAUDE.md` documents annotated-tagging the merge commit at each
  release
- [ ] TOOLING-003-AC-05 [AUTO]: `application.yaml` sets `server.shutdown: graceful`
- [ ] TOOLING-003-AC-06 [AUTO]: `.editorconfig` matches real existing indentation conventions
  exactly, zero diff noise
- [ ] TOOLING-003-AC-07 [AUTO]: `.github/pull_request_template.md` exists with a repo-appropriate
  (not enterprise) checklist

## Test case sketches

Like the other tooling specs, this is documentation/config with no backend-Java/frontend-TS test
harness to wire into — verification is the "Test Case (Green)" step under each AC above, performed
once during implementation and reported.

```
1. Write README.md, confirm every documented command actually runs as written.
2. Add the Conventional Commits + release-tag sections to CLAUDE.md.
3. Add server.shutdown: graceful to application.yaml; restart the backend and confirm the startup
   log shows graceful shutdown is enabled.
4. Write .editorconfig; open one file of each covered type and confirm no reformatting is
   suggested.
5. Write .github/pull_request_template.md; preview it on GitHub.
6. (Separate, explicit go-ahead required before pushing) retroactively tag v0.2.0 through v0.5.0
   against their real merge commits, and push the tags.
```

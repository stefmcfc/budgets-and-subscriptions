# Agents, Steering, and Skills

Tracks the design discussion for this project's `.claude/agents/`, `.claude/steering/`, and
`.claude/skills/` setup — same append-as-we-go convention as `02-open-questions.md`. Nothing in
this file is a file yet; it's the discussion that precedes writing one.

## Research: how the two reference projects (`behavioural-activation`, `series-recommendation`) do this

Read both in full before any of the discussion below. Summary:

**Agents** — 3, nearly identical between both projects, each a thin YAML-fronted markdown file
(`name`, `description`, `tools`) that points at steering docs/specs rather than duplicating them:
- `spec-writer` — drafts EARS-format specs, hands off to the other two, never implements.
- `backend-dev` — implements backend changes via red/green TDD. Both reference projects let Claude
  write the *entire* backend (no human-hand-codes split like ours), so this doesn't transfer
  directly — see below.
- `frontend-dev` — implements frontend changes via red/green TDD (Vitest, mocked service layer).
  Direct match for us — we've already agreed Claude has free rein on `frontend/`.

Both dev agents accumulate a "Static-analysis / Sonar cleanup patterns" section over time — real
lessons from past cleanup passes, baked into the agent file so they're reused rather than
re-derived next time the same class of finding shows up.

**Skills** — 2, same in both projects:
- `ears-spec` — how to draft a new spec in their EARS format (patterns, ID scheme, verification
  markers, numbering, "check the file doesn't already exist before `Write`").
- `verify` — a project-specific launch/drive/gotchas runbook for manually exercising the app
  end-to-end (ports, curl recipes, CORS workarounds, dark-mode/a11y checks). Grows over time as
  more of the app becomes driveable.

**Steering docs** (`.claude/steering/`) — living documents, explicitly marked "what's built vs.
still target," corrected when drift is found rather than left stale:
- `product.md` — what it does, who it's for, goals, non-goals, known constraints.
- `structure.md` — backend package layout, naming conventions, where tests live.
- `tech.md` — exact versions/dependencies (checked against the real build file), commands, CI/CD,
  secrets handling, cross-cutting rules (e.g. `Clock` injection).
- `frontend_structure.md` / `frontend_conventions.md` — same idea for the frontend: target vs.
  built layout, typing/API-layer/styling/testing/a11y conventions.
- `ears_format.md` — the full spec-authoring reference (patterns, ID scheme, verification markers,
  Spock block-label mapping).

**Specs** (`.claude/specs/`) — one file per feature, `{area}_spec_{number}_{name}.md`, EARS-format
acceptance criteria with immutable `<AREA>-<NNN>-AC-<NN>` IDs, TDD test sketches, tracked to
completion in a root `ROADMAP.md`.

**Ideas pipeline** — `future_ideas.md` (raw, unconfirmed) → `SPEC_CANDIDATES.md` (confirmed worth
building, not yet spec'd) → real spec → `ROADMAP.md` → `CHANGELOG.md` (shipped). Both files carry a
"last reviewed" date and get corrected when referenced code has drifted — not left to rot.

**Audits are not agents.** Neither project has a standing `*-audit` agent. An audit is an ad-hoc,
dated, one-off deep dive (`.claude/audits/audit-YYYY-MM-DD.md`) with explicit Scope/Method/
Disposition sections, ending in a Recommendations list that routes each finding to a tooling spec,
a spec candidate, or a future idea.

## Decisions so far

### spec-writer

**Status: Direct reuse.** Matches the reference pattern as-is (swap product specifics once we
write it). No open questions.

### frontend-dev

**Status: Direct reuse.** Matches the reference pattern as-is — we've already agreed Claude has
free rein on `frontend/` (components, API client code, routing, styling, tests), same as both
reference projects.

### backend-dev (restricted)

No precedent in either reference project — both let Claude write the entire backend; ours
reserves most of it for hand-coding (see root `CLAUDE.md`'s Working Agreement). In scope for this
agent: DTOs, records, package/skeleton classes, and skeleton Spock specs. Not in scope: entities,
repositories, services, controllers, security, real test logic — all human-hand-coded.

> **Question raised (Claude):** the working agreement currently lists "Tests" under things Claude
> must NOT write, but the agent's intended scope includes "skeleton Spock specs" — where's the
> line? Specifically: does a red/green TDD skeleton give away implementation detail if Claude
> writes the `given:`/`when:`/`then:` block structure?
>
> **Discussion:** in the reference projects, an AC drives a test — `backend-dev` reads the spec
> and writes the full Spock spec (real `given`/`when`/`then` content, real assertions) *before*
> implementing, confirms it fails, then implements until green. That's genuinely authoring the
> test, which conflicts with this project's whole point (the human practices TDD themselves).
>
> **Proposed (Claude), pending confirmation:** write the method stub and the three block **labels**
> (lifted directly from that AC's own While/When/shall clauses — information already in the spec,
> not a hint about *how* it's implemented), but leave every block **body** empty:
> ```groovy
> def "SERIES-00X-AC-01: <scenario from the spec>"() {
>     given: "<state, from the AC's While/Where clause>"
>
>     when: "<trigger, from the AC's When clause>"
>
>     then: "<response, from the AC's shall clause>"
> }
> ```
> No setup, mocks, or assertions — filling those in (and getting a true failing test) is the
> human's TDD exercise. This keeps the AC→test traceability the reference projects rely on without
> Claude writing any real test logic. An empty-bodied block alone compiles and passes vacuously —
> it's scaffolding, not an actual red test; the human's first real content is what makes it red.
>
> **Answer:** confirmed, the label-only approach makes sense.
>
> **Status: Resolved.**

### backend-code-reviewer

**Answer:** an on-demand pass at the end of writing an AC — not continuous/inline review during
hand-coding, a discrete review step the human triggers once an AC's implementation is done.

**Follow-up answer:** the reviewer agent supplements `CLAUDE.md`'s existing inline-reviewer
instructions, rather than replacing them — the main session still reviews as changes are made;
this agent is the additional, explicit on-demand deep pass once an AC is complete.

**Status: Resolved.**

### backend-helper

**Answer:** when building a pseudocode skeleton for a struggling human, a dummy return (e.g.
`return null;` / `throw new UnsupportedOperationException();`) is fine to make the stub compile —
resolves the "comment-only method body doesn't compile in Java" technical wrinkle flagged earlier.

**Status: Resolved.**

### frontend-audit / backend-audit

**Answer:** not needed as formal agents/skills — wouldn't be used often enough to justify
formalizing. Matches the reference projects' own pattern anyway (audits there are ad-hoc dated
output, not standing agents).

**Status: Resolved — won't build.** If an audit is ever wanted, default to the reference
projects' lighter pattern instead: an ad-hoc dated file under `.claude/audits/`, not a dedicated
agent.

### Steering docs

**Answer:** adopt all six — `product.md`, `structure.md`, `tech.md`, `frontend_structure.md`,
`frontend_conventions.md`, `ears_format.md` — with relevant adoptions for our stack/split rather
than verbatim copies (e.g. `structure.md` needs to reflect which backend packages are
human-hand-coded vs. Claude-scaffolded; `tech.md` reflects our actual `build.gradle.kts`/
`package.json`, Postgres+Flyway+Docker, oxlint not ESLint).

**Status: Resolved.**

## Re-read: `behavioural-activation`'s steering/skills, now updated past scaffolding

Re-read in full (2026-10-08) after the user updated them in that project. It's moved well past
scaffolding (6 controllers, 11 Flyway migrations, full React app) — patterns worth carrying into
ours once we write our own versions:

- `structure.md` stopped tagging every file `(built)` vs. target as the project grew — past a
  certain size that drifts out of date within days. It now just describes the real layout and
  names only the handful of genuinely-unbuilt areas. Plan for this transition later; start with
  per-item tagging now since we're at scaffold stage.
- `ears_format.md` grew a `Summary` section (between the header and `Overview`, populated once a
  spec hits `Implemented`): real findings, amendments, test counts. Keeps `Status` to one line.
  Worth adopting from the start rather than retrofitting later.
- The `verify` skill now defers to a root `RUNBOOK.md` for exact commands instead of duplicating
  them, once enough commands exist to need one.
- `ears-spec` now explicitly tells the agent to treat steering docs as "a fast orientation pass,
  not a substitute for checking the real source" — even a well-maintained doc is a snapshot.

## backend-helper / backend-code-reviewer: how would they trigger without naming them?

**Question raised (user):** if the user says "I'm stuck trying to do X" or "I've finished AC-xxx",
would the relevant skill/agent fire without explicitly naming it?

**Answer (Claude):** yes — for both skills and agents, triggering is controlled by the
`description` field, matched against the user's actual phrasing by whatever orchestrates the
session, not by the user naming it. Write the description with explicit trigger phrases (the same
way this session's own installed skills do, e.g. `modern-web-guidance`'s "Trigger immediately
for: ... DO NOT trigger for: ..." list) — e.g. `backend-helper`: "Use when the user says they're
stuck, unsure how to approach something, or asks for guidance before writing backend code
themselves." `backend-code-reviewer`: "Use when the user says they've finished an AC, finished
implementing something, or asks for a review of backend code they just wrote."

**Follow-on finding:** skill vs. agent doesn't change enforcement here — in both reference
projects, `backend-dev` has the *same* tool access as `frontend-dev` (`Read, Edit, Write, Bash,
Grep, Glob`); its restraint is purely instructional, not a hard permission boundary. The real
tradeoff is context isolation: an **agent** runs in a separate context (keeps exploration noise
out of the main conversation, but only returns a summary); a **skill** loads inline (shares
context, but every step is visible live). Claude's recommendation: **skill** for
`backend-code-reviewer` (its value is reading the reasoning line-by-line against code just
written, not a condensed summary); `backend-helper` is a closer call — skill while its
doc-suggestion step stays quick, revisit as an agent if it grows into heavier multi-source
research.

**Answer:** skill for both, confirmed.

**Status: Resolved — drafted.** `.claude/skills/backend-helper/SKILL.md` and
`.claude/skills/backend-code-reviewer/SKILL.md` written.

### `ears-spec` / `verify` skills

**Answer:** adopt both, adapted to our area naming (`BACKEND`/`FRONTEND`/`TOOLING` instead of
`SERIES`/`PLANNER`) and our scaffold-stage reality (`verify` is honest that there's nothing real
to drive yet; `ears-spec` tells itself to write backend test-case sketches as scenario +
block-structure only, not full content, matching `backend-dev`'s skeleton convention).

**Status: Resolved — drafted.**

## Everything drafted (2026-10-08)

All of the above is now written:

- **Steering** (`.claude/steering/`): `product.md`, `structure.md`, `tech.md`,
  `frontend_structure.md`, `frontend_conventions.md`, `ears_format.md`.
- **Skills** (`.claude/skills/`): `ears-spec`, `verify`, `backend-helper`,
  `backend-code-reviewer`.
- **Agents** (`.claude/agents/`): `spec-writer`, `backend-dev` (restricted — scaffolding only,
  label-only Spock skeletons), `frontend-dev` (free rein, matches the reference pattern).
- Root `CLAUDE.md` trimmed to a thin entrypoint pointing at the above (mirroring both reference
  projects' "Deep-dive references" pattern) rather than duplicating steering content inline.
- Local dev ports changed to `8090`/`5180` (backend/frontend) to avoid colliding with the two
  reference projects' own ports (`8080`/`5173` and `8420`/`4321`) if ever run side-by-side.

## Still to decide

Nothing outstanding from this discussion. Next real decision points will come from actually using
these agents/skills on the first real spec (build order step 1: auth) — revisit and amend this
file as that surfaces anything that doesn't hold up in practice.

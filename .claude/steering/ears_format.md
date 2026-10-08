# EARS Requirements Format

This project uses EARS (Easy Approach to Requirements Syntax) to write clear, testable
requirements. All specs use EARS format with explicit references, verification markers, and
traceability to tests. This applies from the very first spec — there's no legacy ID scheme to
carry forward.

## EARS Patterns

Every requirement statement follows one of the five canonical EARS patterns. Use the simplest one
that captures the requirement — complex ACs may combine clauses (When … , if … , then …).

| Pattern | Template | Example |
|---|---|---|
| Ubiquitous | The `<system>` shall `<response>` | The `AccountEntity` shall default `currency` to `GBP` |
| Event-driven | When `<trigger>`, the `<system>` shall `<response>` | When `POST /api/v1/accounts` is requested, the `AccountController` shall create and return the new account |
| State-driven | While `<state>`, the `<system>` shall `<response>` | While a fetch is in flight, the `AccountList` component shall display a loading spinner |
| Unwanted behaviour | If `<condition>`, then the `<system>` shall `<response>` | If `accountApi.getAll()` rejects, then the `AccountList` component shall display an error message with a Retry button |
| Optional feature | Where `<feature is present>`, the `<system>` shall `<response>` | Where a transaction matches a known merchant pattern, the classification engine shall suggest a category |

**Name the system concretely** — the component, endpoint, service class, or entity. Never a bare
"the system".

## Reference IDs

Every acceptance criterion gets a unique, human-readable ID in the form:

```
<AREA>-<SPEC-NUMBER>-AC-<NN>
```

- `AREA` is `BACKEND` for backend specs, `FRONTEND` for frontend specs, or `TOOLING` for
  repo-wide tooling/CI/build-config specs that aren't backend or frontend feature work — matching
  the spec file's own prefix (`backend_spec_005_*.md` → `BACKEND-005`, `frontend_spec_003_*.md` →
  `FRONTEND-003`, `tooling_spec_001_*.md` → `TOOLING-001`).
- `NN` is a two-digit sequence number within that spec, assigned in the order requirements appear.

Example: `BACKEND-005-AC-01`, `FRONTEND-003-AC-07`.

This mirrors the spec filename directly rather than inventing a separate stage system. A spec's
own filename is already the short, meaningful "what's being changed" label, so the ID just needs
to be traceable back to it.

### Conversion rules

1. **Reference IDs are immutable.** Once assigned, never renumber, merge, or delete an ID — other
   specs, tests, and cross-reference tables may point to it.
2. **Splitting**: if one requirement contains several distinct obligations, use sub-letters under
   the same ID (`BACKEND-005-AC-01a`, `BACKEND-005-AC-01b`) rather than new numbers.
3. **No weakening**: every obligation in a requirement's prose must survive into its AC
   statement(s). If a requirement is ambiguous, resolve toward the stricter reading and note the
   decision in the spec.

## Verification markers

Every AC carries a marker immediately after its reference ID, stating how it's actually verified:

- `[AUTO]` — verified by an automated test (Spock spec, Vitest test) or the build pipeline itself.
- `[MANUAL]` — verified by human review. A `[MANUAL]` AC must state *how* it's checked (e.g.
  "visual check in browser against the design note above") and, where one exists, note the route
  to automating it later.

`[AUTO]` should be the overwhelming majority — both backend (Spock) and frontend (Vitest + RTL)
support testing almost everything. Treat any new `[MANUAL]` as something to justify, not a
default.

## Structure of a spec

Each `.claude/specs/*.md` file has:

1. **Header**: title, `Status`, `Priority`, `Depends on`, backend/frontend area. **`Status` stays
   to one line** — `Not started` / `In progress` / `Implemented (date)`, optionally with a pointer
   to implementing files. It is not the place for test counts, real findings, or amendment
   history — see `Summary` below for where that goes.
2. **Summary** (present once a spec reaches `Implemented`; omitted while `Not started`/`In
   progress`): the home for everything that would otherwise get bolted onto `Status` — test
   counts, real findings, bugs found and fixed, same-day amendment rounds, real-browser
   verification notes. Positioned directly after the header, before `Overview`. Use subheadings
   and bullet lists, not a dense paragraph. If nothing noteworthy happened during implementation,
   a one-or-two-sentence `Summary` is fine.
3. **Overview**: one paragraph — what this delivers and why, written before implementation. Left
   untouched once a spec ships — it's the historical record of original intent, not updated to
   reflect what actually happened (that's `Summary`'s job).
4. **Requirements**: grouped "Requirement N" sections, each with a one-line user story and its
   EARS-format acceptance criteria (ID + `[AUTO]`/`[MANUAL]` marker + statement).
5. **Cross-references**: a table linking to the specific endpoints, types, or specs this one
   depends on or contracts against.
6. **TDD test case sketches** (red, before implementation) in the target framework — Spock
   `given/when/then` for backend, Vitest + RTL for frontend — one per AC, named after its
   reference ID. **For a backend spec, this sketch is what `backend-dev` turns into a skeleton**
   (labelled blocks, no bodies) — see that agent's file.
7. **Acceptance Criteria Summary**: a flat checklist mirroring every AC above, unchecked
   (`- [ ]`) until implemented.

### Header + Summary template (once Implemented)

```markdown
# Create an Account (Backend)

**Status**: Implemented (2026-11-02)
**Priority**: P1 — core build-order step 2 flow
**Depends on**: `backend_spec_001_auth.md` (session/JWT auth every endpoint inherits)
**Area**: Backend

## Summary

All 12 ACs implemented and tested (18 Spock tests, 0 regressions). Owner resolved from the
authenticated principal, never the request body.

**Real findings**:
- An invalid `accountType` value returned `500` instead of `400` — `GlobalExceptionHandler` now
  handles `HttpMessageNotReadableException`.

## Overview

...
```

### AC template

```markdown
### BACKEND-005-AC-01 [AUTO]: Create an Account
**Statement**: When `POST /api/v1/accounts` is requested with a valid body, the
`AccountController` shall create and return the new account with `201 Created`.

**Rationale**: Users need an account to exist before transactions can be logged against it.

**References**:
- Type: `AccountDto` (backend `dto/`), `Account` (frontend `src/types/account.ts`)
- Related: `BACKEND-005-AC-02` (validation failure case)

**Test Case (Red)**:
\```groovy
def "BACKEND-005-AC-01: creates and returns a new account"() {
    given: "a valid account request body"

    when: "POST /api/v1/accounts is requested"

    then: "the response is 201 with the created account"
}
\```

**Test Case (Green)**: implement the controller/service until the spec above passes.
```

Note the Test Case (Red) sketch above is intentionally body-less in this template — the spec
itself only needs to show *which* scenario and block structure the test will cover.
`backend-dev` turns this into the real skeleton file; filling in real `given`/`when`/`then`
content and assertions is the human's TDD exercise (see `structure.md`'s "Who writes what").

## Mapping to Spock

- **While/Where** (state, preconditions) → `given:` block
- **When** (trigger) → `when:` block
- **shall** (response) → `then:` assertions
- **If/then** (unwanted behaviour) → `when:` + `then:` with `thrown(...)` or error-status
  assertions

A ubiquitous requirement typically becomes a `then:`/`expect:`-only spec.

### Block labels

Every `given:`/`when:`/`then:`/`expect:`/`and:` block in a Spock spec carries a string label
describing that step in plain language — bare, unlabelled blocks aren't used. Where a block maps
directly onto an EARS clause, the label echoes that clause's own wording:

- `given "<state>":` — mirrors a While/Where clause, or states setup when the AC has no explicit
  precondition.
- `when "<trigger>":` — mirrors the When clause.
- `then "<response>":` — mirrors the shall clause.
- `and "<...>":` — a further assertion or action within the same phase; label it independently.
- `expect "<response>":` — collapses when+then into one block for a direct, side-effect-free
  assertion; still labelled.

```groovy
def "BACKEND-005-AC-01: creates and returns a new account"() {
    given: "a valid create-account request"
        def request = new AccountRequest(name: "Current Account", type: AccountType.BANK_CURRENT)

    when: "POST /api/v1/accounts is requested"
        def response = client.post().uri("/api/v1/accounts").body(request).exchange()

    then: "the response is 201 Created"
        response.expectStatus().isCreated()

    and: "the created account is returned in the body"
        response.expectBody().jsonPath("$.data.name").isEqualTo("Current Account")
}
```

This applies to every Spock spec in `backend/src/test/groovy/` — including the label-only
skeletons `backend-dev` produces, where the labels are present but the code beneath them is left
for the human to write.

## Before writing a new spec file

**Verify the target file doesn't already exist before calling `Write` on it** — via `Read` or
`Glob`, not by trusting another spec's cross-reference table or an assumption that it's "not yet
written." A blind `Write` silently clobbers an existing draft, which is especially destructive
here because reference IDs are immutable and other specs/tests may already point at the ones a
clobbered file was carrying. Default to `Edit` (which enforces a prior `Read`) for anything that
might already exist; only use `Write` once confirmed, this session, that the path is genuinely
new.

## Naming convention for frontend test files

Group tests in one file, using `describe` blocks named after the requirement ID:

```typescript
describe('FRONTEND-003-AC-01: fetch on mount', () => { /* tests */ })
describe('FRONTEND-003-AC-02: loading state', () => { /* tests */ })
```

## Why EARS + TDD together

1. **Traceability**: every test corresponds to a requirement ID.
2. **Clarity**: no ambiguity about what "done" means.
3. **Testability**: EARS statements are inherently testable.
4. **Reviews**: reviewers (including `backend-code-reviewer`) can verify against requirement IDs.

## When writing a new spec

Always include:
1. Requirement statements in EARS format, each with a `<AREA>-<NNN>-AC-<NN>` ID and
   `[AUTO]`/`[MANUAL]` marker
2. References to backend/frontend types, endpoints, and related specs
3. An Acceptance Criteria Summary checklist
4. Test case sketches showing red/green structure, named after the requirement ID

The `.claude/skills/ears-spec` skill packages this workflow — use it when drafting a new spec so
the structure stays consistent.

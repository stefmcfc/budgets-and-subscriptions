---
name: ears-spec
description: Draft a new EARS-format feature spec for Budgets and Subscriptions (backend or frontend) and save it to .claude/specs/. Use when the user asks to spec out a new feature, write requirements for something, or plan work before implementing it.
---

# Writing an EARS-format spec

This project's specs (`.claude/specs/`) all follow EARS (Easy Approach to Requirements Syntax).
Full reference: `.claude/steering/ears_format.md` — read it before drafting if you haven't
already in this session.

**This is the `spec-writer` agent's core workflow.** If invoked directly rather than via that
agent, follow the same constraint it does: write the spec, don't implement it.

## Steps

1. **Confirm scope with the user** if it's not already clear: is this a backend spec
   (Java/Spring/Spock) or frontend spec (React/TS/Vitest)? What does it depend on? Check
   `.claude/specs/` for the next available number in the right sequence (`backend_spec_00N_*` or
   `frontend_spec_00N_*`).

2. **Ground it in reality, not assumption.** Before writing requirements:
   - Backend: read `.claude/steering/structure.md` and `.claude/steering/tech.md`, and grep the
     actual `backend/src/main/java/...` for related existing classes — this project is scaffold-
     only as of 2026-10-08, so don't assume anything beyond the entry point class exists without
     checking.
   - Frontend: read `.claude/steering/frontend_structure.md` and
     `.claude/steering/frontend_conventions.md`, and check what actually exists under
     `frontend/src/` — same caveat, only the default Vite starter exists so far.
   - Check dependency specs' actual `Status` line and cross-check against the real source.
   - **Remember the backend split** (`.claude/steering/structure.md`'s "Who writes what"): a
     backend spec's test case sketches become a label-only skeleton for the human to fill in, not
     a fully-written test — write the sketch as a scenario + block structure, not full
     `given`/`when`/`then` content (see the AC template in `ears_format.md`).

3. **Write requirements as numbered EARS statements**, grouped into named "Requirement N"
   sections, each with a one-line user story and acceptance criteria using the canonical EARS
   patterns (Ubiquitous, Event-driven, State-driven, Unwanted behaviour, Optional feature). Assign
   each AC an ID in the form `<AREA>-<SPEC-NUMBER>-AC-<NN>` (`BACKEND-005-AC-01`,
   `FRONTEND-003-AC-02`, ...) plus a `[AUTO]`/`[MANUAL]` verification marker — see
   `.claude/steering/ears_format.md` for the full scheme.

4. **Include a cross-reference table** linking to the specific endpoints, types, or specs this one
   depends on or contracts against.

5. **Include TDD test case sketches** (red, before implementation) in the target framework — Spock
   `given/when/then` for backend, Vitest + React Testing Library for frontend — one per major
   acceptance criterion, named after its requirement ID.

6. **End with a flat "Acceptance Criteria Summary" checklist** mirroring every criterion above,
   unchecked (`- [ ]`).

7. **Save** to `.claude/specs/{area}_spec_{number}{_name}.md` and tell the user what to hand off
   next — usually `backend-dev` or `frontend-dev` to produce the scaffolding/skeleton, with the
   human then hand-coding the real backend implementation (frontend: `frontend-dev` implements it
   directly, per the Working Agreement).

## What NOT to do

- Don't mark anything as done/implemented in the new spec — it hasn't been built yet.
- Don't invent API contracts that don't match what's actually in
  `backend/src/main/java/uk/co/stefirby/budgetsandsubscriptions/dto/` — check the real DTOs (once
  they exist).
- Don't skip the test case sketches — `backend-dev`/`frontend-dev` rely on them as the starting
  point.
- Don't write a backend test case sketch with full given/when/then content — that's the skeleton
  step's job to turn into labels, and the human's job to fill in for real; see step 2 above.

**Before calling `Write` on the target path, confirm it doesn't already exist** (`Read` or `Glob`
it) — don't rely on another spec's cross-reference table or an assumption that a given number is
unused. If the path already exists, use `Edit` or stop and flag the collision instead of
overwriting.

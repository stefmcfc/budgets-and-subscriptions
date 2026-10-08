---
name: backend-code-reviewer
description: Use when the user says they've finished an acceptance criterion (AC), finished implementing something in the backend, or asks for a review of backend (Java/Spring Boot/Spock) code they just wrote. An on-demand, end-of-AC review pass — supplements, not replaces, the inline reviewer behavior root CLAUDE.md's Working Agreement already describes for the main session. Starts high-level; only goes deeper if asked.
---

# Reviewing hand-coded backend work

Use for a deliberate, discrete pass once the user says an AC (or a chunk of backend work) is
done — not for reviewing mid-write. The main session already reviews inline as changes happen per
`CLAUDE.md`'s Working Agreement; this skill is the additional, explicit deeper pass at a natural
checkpoint.

## Steps

1. Identify what changed — the relevant AC (`.claude/specs/...`) if named, otherwise `git diff` or
   the files the user mentions.
2. **First pass, high-level only:**
   - Correctness against the AC's acceptance criteria.
   - Security issues (auth, validation, SQL injection, sensitive-data handling — this project
     exists in part to learn this properly, so don't skip it even for small changes).
   - Style against this project's own conventions (`.claude/steering/structure.md`, `tech.md`) and
     the global Java/Spring defaults (`~/.claude/CLAUDE.md`).
   - Missed edge cases that are obvious at a glance.
3. Report findings as a plain list — what's good, what's worth changing, and why. Don't fix
   anything yourself unless explicitly asked to.
4. **Only go line-by-line or suggest specific code if the user asks for that level of detail** —
   default to describing the issue and why it matters, not supplying the fix.
5. If the AC's Spock skeleton (written by `backend-dev` — labelled `given`/`when`/`then` blocks,
   no bodies) hasn't actually been filled in with real content and run red before the
   implementation went green, flag that specifically — skipping the red phase defeats the TDD
   discipline this project exists to practice.

## What NOT to do

- Don't rewrite the code yourself — point out the issue, explain why it matters, and let the user
  fix it.
- Don't review continuously during hand-coding — this is a discrete, requested pass, not a
  running commentary alongside their work.
- Don't skip straight to low-level nitpicks before covering correctness/security/style at a high
  level first — depth is opt-in, not the default.

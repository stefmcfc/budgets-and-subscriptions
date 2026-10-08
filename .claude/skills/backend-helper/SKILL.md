---
name: backend-helper
description: Use when the user is stuck, unsure how to approach something, or wants guidance before writing backend (Java/Spring Boot/Spock) code themselves — before it's written. Suggests where to look (official docs, relevant classes/methods, articles), not what to write. Not for reviewing code that already exists (see backend-code-reviewer) and never implements anything itself.
---

# Helping with backend work before it's written

This project's Working Agreement (root `CLAUDE.md`) reserves almost everything backend-related —
entities, repositories, services, controllers, security, real tests — for the user to hand-code.
This skill's job is to unblock them without writing it for them.

## Steps

1. Understand what they're trying to do and where they're actually stuck. Ask if it's unclear —
   don't guess at the blocker.
2. **First response: point at where to look, not what to write.** Specific Javadoc, the relevant
   Spring Framework/Spring Boot reference page, Spock documentation, concrete class/method names
   worth reading. Prefer linking to the canonical docs over summarizing them yourself — the point
   is for the user to read and understand, not to receive a pre-digested answer that skips that
   step.
3. **Only if they come back still stuck after trying that**, build a skeleton: method
   signature(s) only, with commented pseudocode describing the steps in plain English, and a dummy
   body just sufficient to compile (`return null;`, `throw new UnsupportedOperationException();`)
   — never real Java/Groovy logic.
4. Stay high-level first. Only go deeper (pointing at a specific line-level approach) if they
   explicitly ask for more detail after the first pass.

## What NOT to do

- Don't write working code "to save time" — the explicit point of this project is for the user to
  practice writing it themselves.
- Don't jump straight to a pseudocode skeleton — try pointing at docs/resources first; the
  skeleton is the fallback, not the default.
- Don't write real method bodies, real Spock test content, or any entity/repository/service/
  controller/security code — all reserved for the human per `CLAUDE.md`, regardless of how stuck
  they are.
- Don't review code that already exists — that's `backend-code-reviewer`'s job, not this skill's.

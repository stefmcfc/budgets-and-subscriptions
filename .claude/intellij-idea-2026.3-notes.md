# IntelliJ IDEA 2026.3 — what's actually useful here

Reviewed [What's New in IntelliJ IDEA 2026.3](https://www.jetbrains.com/idea/whatsnew/) against
this project's real stack (Java 25 LTS, Spring Boot 4.1.1, Spring Data JPA + Hibernate, Spring
Security (planned), Flyway, PostgreSQL 17, Spock tests, Gradle wrapper, React 19 + Vite frontend)
and the [Working Agreement](../CLAUDE.md) (human hand-codes entities/repos/services/
controllers/security/tests in `backend/`; Claude scaffolds/specs; Claude has free rein in
`frontend/`).

## Worth turning on now

**Hibernate debugger** (separate plugin, install from Marketplace: `Hibernate Debugger`).
Steps through what Hibernate actually does at the persistence layer — lets you replay the exact
SQL a query produced, or open a pre-filled HQL/JPQL console query from a breakpoint. Directly
useful once entities/repositories exist, especially for catching N+1s or confirming what a
`JpaRepository` method actually generates — exactly the kind of thing worth understanding by hand
rather than guessing at, which fits the "learn Spring Boot properly" goal of this repo.

**Spring Security diagnostics in HTTP responses.** When a request gets a 401/403 or an unexpected
login redirect, the IDE can now surface *which* security rule rejected it (via an
`x-spring-debugger-security-decisions` response header/inspector), instead of you tracing through
the filter chain by hand. Auth (Core Feature — JWT via httpOnly cookie, `USER`/`ADMIN` roles) is
the very next build-order item after accounts/transactions, so this will be relevant soon and is
worth having on by default when that work starts.

**Terminal tabs in the editor + the libghostty terminal rewrite.** The new terminal backend
(`libghostty`) specifically fixes reliability for "terminal-based agents such as Claude Code" —
the release notes name Claude Code explicitly as a workload this was fixed for. Being able to pop
a terminal tab out next to source files also gives a running `gradlew.bat bootRun` or `npm run dev`
more room without losing the editor. Low-effort, pure quality-of-life — worth adopting immediately
given Claude Code is run from this machine constantly.

**Git worktrees for parallel work and code review.** Lets you check out another branch into its
own folder/window without disturbing the current one — useful for reviewing a PR (e.g. this repo's
`backend/transactions-view` → `backend/transactions-update` branch flow) side-by-side with work in
progress, without stashing. IntelliJ 2026.3 also respects a `.worktreeinclude` file so
worktree-local IDE config carries over correctly. Minor workflow upgrade, no project changes
needed to benefit.

## Situational — relevant once specific work starts

- **Spring cache support in Spring Debugger** (shipping 2026.3.1, not yet in this release) — adds
  gutter actions for `@Cacheable` methods so a breakpoint doesn't silently stop firing after the
  first cached call. Not used yet, but spending-insights/summary work (Core Feature 3) is a
  plausible future `@Cacheable` candidate — worth remembering once that's built.
- **`ij-debugger` agent skill + JetBrains Air's ACP support.** Air explicitly detects
  ACP-compatible agents (the notes list Claude Agent among them) and the `ij-debugger` skill grants
  a connected agent direct access to debug configs, logpoints, and runtime diagnostics. This repo
  already drives backend work through Claude Code rather than Air/Junie, so it's not a must-switch
  — but if IntelliJ's own agent panel is ever used alongside Claude Code here, this is the piece
  that would let an agent drive the debugger directly rather than you relaying stack traces by
  hand.
- **Chrome DevTools Connect** (plugin) — gives an AI agent direct browser-inspection access
  (console, network, DOM) while working in WebStorm/IntelliJ's JS tooling. Relevant to
  `frontend/` once there's actual UI to debug interactively; not needed for the current bare Vite
  scaffold.
- **New Database Output tool window** — query/run-configuration results get a dedicated window
  instead of sharing the Services tool window. Minor but genuinely useful once Flyway migrations
  and real entities exist and you're running ad hoc queries against the `:5433` Postgres container.
- **Quick tasks and edits without opening a project** — a lightweight scratch-file window for
  one-off snippets. Nice-to-have, not project-specific.

## Not relevant right now

- **Java 28 (early access)** — this project is pinned to Java 25 LTS deliberately (global Java
  defaults: never bump to a non-LTS version here). Java 28 isn't LTS either, so this stays
  irrelevant until the next LTS release.
- **Kotlin support for Spring Modulith**, **Scala plugin improvements** — no Kotlin or Scala in
  this repo.
- **Jakarta Persistence 4 / Hibernate 8 console-query support** — worth re-checking once the
  project's actual bundled Hibernate version (whatever Spring Boot 4.1.1 pulls in) is confirmed;
  likely not on Hibernate 8 yet.
- **Remote development, file navigation inside Kubernetes pods, cross-database queries, DuckDB
  support** — nothing in this project runs remotely, in containers (beyond the one local Postgres
  container), or needs cross-database joins.
- **JetBrains Air / Junie Lite** (the IDE's own built-in agent layer) — this repo's whole backend
  workflow is already structured around Claude Code doing the AI side under the Working Agreement.
  Adopting Air as a second, parallel agent driver would just create two AI entry points into the
  same hand-coded-by-human backend; not worth it unless that working agreement changes.
- **Figma/Storybook Connect, sound cues, accessibility audio cues** — not applicable to this
  project's current needs.

## Suggested next step

Install the **Hibernate Debugger** plugin now (useful immediately once entities/repositories land
in Core Feature 1), and keep the **Spring Security diagnostics** feature in mind for when auth
implementation starts. Everything else above is either already-on-by-default (terminal, worktrees)
or fine to revisit later when the relevant feature work actually begins.

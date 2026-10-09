# Tech Stack

**Status: scaffolded, no domain code yet.** This reflects what's actually declared in
`backend/build.gradle.kts` and `frontend/package.json` as of 2026-10-08 — check those files
directly if this drifts.

## Language / Runtime

### Backend
- **Java 25** toolchain (LTS — never bump to a non-LTS version in this repo, per global Java
  defaults)
- **Spring Boot 4.1.1**

### Frontend
- **TypeScript** ~6.0
- **React** 19.2
- **Vite** 8

## Framework

### Backend
- **Spring Boot**: `spring-boot-starter-data-jpa`, `spring-boot-starter-security`,
  `spring-boot-starter-validation`, `spring-boot-starter-webmvc` (Boot 4.1's modular starter name
  — not the old `-web`), `spring-boot-starter-flyway`, plus
  `spring-cloud-starter-circuitbreaker-resilience4j`. Matching modular test-slice starters
  (`-data-jpa-test`, `-flyway-test`, `-security-test`, `-validation-test`, `-webmvc-test`) are
  `testImplementation`. Pulled from `behavioural-activation`'s own working `build.gradle.kts`
  (confirmed-correct Boot 4.1 artifact names) rather than guessed.
- **No Lombok** — plain Java, records for small immutable DTOs (per global Java defaults).

### Frontend
- **React 19**: component-based UI
- **react-router-dom**: routing
- **axios**: HTTP client, used only inside the API service layer (see
  `frontend_conventions.md`) — never called directly from a component
- Styling approach: **CSS Modules** (adopted now, matching both reference projects — one
  `ComponentName.module.css` colocated per component). Revisit only if a concrete need for
  something else (Tailwind, etc.) shows up.

## Database

**PostgreSQL**, local dev via Docker — **its own dedicated container**, deliberately not shared
with `behavioural-activation`'s Postgres container. Reason: that project's own `verify`-equivalent
workflow recommends `docker compose down -v` to reset test data, which would wipe both projects'
data if they shared a container — the isolation is worth the (negligible) extra container.

- `docker-compose.yml` (repo root): `postgres:17-alpine`, user/password/db all
  `budgetsandsubscriptions`, host port **`5433`** (not the default `5432` — that's already bound
  by `behavioural-activation`'s own container when it's running). `docker/init-test-db.sql`
  creates a sibling `budgetsandsubscriptions_test` database on first container init, for the
  backend's test profile.
- `backend/src/main/resources/application.yaml` (dev) and
  `backend/src/test/resources/application.yaml` (test) both point at `localhost:5433`, env-var
  overridable (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) — checked-in values are local-dev placeholders,
  not real secrets. A second test-resources file, `application-test-db.yaml`, is loaded on top of
  the base one whenever a spec is annotated `@ActiveProfiles("test-db")` — needed by
  `@DataJpaTest` specs that use `@AutoConfigureTestDatabase(replace = Replace.NONE)` to hit the real
  Postgres test database instead of an auto-swapped embedded one (e.g. `UserSpec`, for AC-01's
  DB-level unique constraint).
- **Spring Data JPA + Hibernate** as the ORM layer. `ddl-auto: validate` is set (Hibernate checks
  mapped entities against the real schema rather than generating one) — now load-bearing, not just
  harmless scaffolding, since `User` is a real entity validated against `V001`'s migrated schema on
  every context load. Schema changes come from a new Flyway migration, never from Hibernate
  auto-DDL.
- **Flyway** for migrations — `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`
  are both in `build.gradle.kts` (the Postgres-dialect module doesn't come bundled with the
  starter alone). `spring.flyway.locations` is already set in `application.yaml`, pointing at
  `classpath:db/migration`. `V001__create_users_table.sql` is the first real migration, creating
  `users` with a `lower(email)` case-insensitive unique index.
- Every entity will carry an owner/user reference once auth exists (see `product.md`'s
  multi-user-readiness rationale) — this is a readiness seam, not multi-tenancy itself.
- **Gotcha — Docker Desktop's daemon isn't always running even when the CLI is installed.**
  `docker compose up -d` fails with a `dockerDesktopLinuxEngine` pipe error if the daemon isn't
  started — start Docker Desktop manually first, same gotcha as `behavioural-activation`.

## Auth

**Decided, not yet built.** JWT, delivered via an **httpOnly cookie** (not `localStorage` —
safer against XSS), with **refresh tokens**. Multi-user with `USER`/`ADMIN` roles; registration is
a real endpoint (not admin/seed-only), and an admin role can view/edit/delete other users'
accounts. Still needs a design pass before implementation: token lifetimes, refresh
rotation/reuse detection, exact admin permissions — see `.claude/00-initial-setup/02-open-questions.md`.

## Build Tools

### Backend
- **Gradle** via the wrapper. Only `gradlew.bat` (Windows) is checked in — never plain
  `gradlew`/`./gradlew`.

### Frontend
- **Vite**: dev server, production build, asset bundling
- **npm**: package manager

## Testing

### Backend
- **Spock 2.4-groovy-5.0** on **Groovy 5.1.1** (`spock-core`, `spock-spring`), the `groovy` Gradle
  plugin, TDD discipline — all in `build.gradle.kts`, versions centralized as `val groovyVersion`/
  `val spockVersion` at the top of the file. No `*.groovy` test sources exist yet
  (`compileTestGroovy` is a `NO-SOURCE` task until the first one is written). Not JUnit/Mockito,
  per global defaults — `junit-platform-launcher` is the underlying test runner Spock sits on top
  of.
- The existing `BudgetsAndSubscriptionsApplicationTests` (JUnit) context-loads test hasn't been
  converted to a Spock spec yet — that conversion is a real-test change, so it's the human's to
  make once they start, not something scaffolded ahead of them.
- Colocated `*Spec.groovy` under `src/test/groovy/...`, mirroring the main package structure 1:1,
  one spec per class under test (see `structure.md`).

### Frontend
- **Vitest 5** + **React Testing Library** + **@testing-library/user-event** — already wired up
  (`npm test`, `npm run test:watch`, `npm run test:coverage`).
- **jsdom** as the test environment. Real-browser verification is still required for anything
  CSS/rendering-related before calling UI work done — jsdom doesn't render CSS (see
  `frontend_conventions.md`).

## Local ports

Pinned to non-default ports, since the two reference projects on this machine already claim the
defaults (`8080`/`5173` and `8420`/`4321`):

- **Backend**: `8090` (`server.port` in `application.yaml`)
- **Frontend**: `5180` (`server.port` in `vite.config.ts`, `strictPort: true`)

The Vite dev-server proxy (`/api` → `:8090`) is already configured in `vite.config.ts`. If either
port ever needs to change, update both together.

## Secrets

Nothing requires a secret yet (no auth implementation, no external API calls), but the rule is
stated now so it's already in place once that changes:

- Never commit `.env`, API keys, or credentials.
- Postgres credentials are already local-dev placeholders in `application.yaml` (and
  `docker-compose.yml`), overridable via env vars — production should always override them, never
  rely on checked-in defaults. The JWT signing secret will need the same treatment once auth is
  built.
- If a variable needs documenting, add it to a checked-in `.env.example` with a placeholder value.

## Hosting

Local only for now — no cloud deployment decided or needed (see `product.md`'s known constraints).

## CI/CD

Not set up yet — no git remote exists either (see root `CLAUDE.md`). Add once there's a remote
and code worth gating.

## Common Commands

```bash
# Local infra (from repo root)
docker compose up -d           # Postgres on :5433 (+ creates a *_test DB on first init)

# Backend (from backend/)
gradlew.bat bootRun            # start dev server on :8090
gradlew.bat test               # run Spock tests (once added)
gradlew.bat build               # full build

# Frontend (from frontend/)
npm install
npm run dev                    # Vite dev server on :5180, proxies /api to :8090
npm test                       # Vitest, single run
npm run test:watch             # Vitest watch mode
npm run test:coverage          # Vitest with coverage report
npm run lint                   # oxlint
npm run build                  # production build
```

## Notes

- **Date/time "now" calls take an injected `Clock`, not an implicit default** — a single `Clock`
  bean (`config/ClockConfig.java`, `Clock.systemDefaultZone()`) for constructor injection, calling
  the `Clock`-accepting overload rather than the no-arg one (flagged by SonarQube `java:S8688`).
  Per global Java defaults — set this up as part of the first service that needs "now."

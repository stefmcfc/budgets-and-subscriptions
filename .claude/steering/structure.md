# Project Structure

**Status: scaffolded only.** `backend/` has the Spring Boot entry point class and an empty
`application.yaml`; `frontend/` has a bare Vite + React + TS starter. No domain package exists on
either side yet. Update this doc as real packages/directories land — don't let it drift into
describing a target that was never built, the way an early scaffolding doc can.

## Layout

```
budgets-and-subscriptions/
├── .claude/                    # agents/, skills/, steering/, 00-initial-setup/ (decision history)
├── backend/                    # Spring Boot application
├── frontend/                   # React + Vite application
├── docker/
│   └── init-test-db.sql        # creates the _test DB on first Postgres container init
├── docker-compose.yml          # Local Postgres, own dedicated container (not shared with
│                                #   behavioural-activation's), port 5433
└── CLAUDE.md                   # Root steering entrypoint
```

## Who writes what (read this before touching `backend/`)

Root `CLAUDE.md`'s Working Agreement is the authority here; this is the package-level
consequence of it. **This split doesn't exist in either reference project this setup was modeled
on** — both let their AI assistant write the entire backend. Here it's deliberately restricted so
the human practices writing it:

| Package | Who writes it |
|---|---|
| `dto/` (records), skeleton `model/`/`service/`/`repository/`/`controller/` classes (empty, package + class declaration only) | Claude may scaffold |
| `model/` (entities), `repository/`, `service/`, `controller/`, `security/`, `exception/`, `config/` — real content | Human hand-codes |
| `src/test/groovy/...` — skeleton (labelled, empty `given`/`when`/`then` blocks, no real content) | Claude may scaffold, per an approved spec's test case sketches |
| `src/test/groovy/...` — real test content (setup, assertions) | Human hand-codes |

Claude's agent for this side (`backend-dev`) only ever produces the left column. See that agent
file for the exact scaffolding contract.

## Target backend structure

Standard layered architecture (per global Java/Spring defaults), once real code exists:

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── uk/co/stefirby/budgetsandsubscriptions/
│   │   │       ├── controller/   # Thin @RestController classes, delegate to services only
│   │   │       ├── service/      # Business logic (@Service), @Transactional on mutations
│   │   │       ├── repository/   # Plain JpaRepository<Entity, UUID> extensions
│   │   │       ├── model/        # JPA entities (@Entity) + enums
│   │   │       ├── dto/          # API-facing shapes — records, centralized, never redeclared inline
│   │   │       ├── exception/    # Custom exception types + GlobalExceptionHandler (@ControllerAdvice)
│   │   │       ├── security/     # Spring Security config, JWT issuance/validation, USER/ADMIN roles
│   │   │       ├── config/       # ClockConfig (injectable Clock bean), CorsConfig, other @Configuration
│   │   │       └── BudgetsAndSubscriptionsApplication.java  # Entry point
│   │   └── resources/
│   │       ├── application.yaml       # Spring config
│   │       └── db/
│   │           └── migration/         # Flyway migrations, V001__... onward
│   └── test/
│       ├── groovy/
│       │   └── uk/co/stefirby/budgetsandsubscriptions/
│       │       └── {controller,service,repository,model,security,config,exception}/  # One *Spec.groovy per class
│       └── resources/
│           └── application.yaml       # Test profile
├── gradle/wrapper/                    # Gradle wrapper (Windows gradlew.bat only)
├── build.gradle.kts
├── settings.gradle.kts
└── .gitignore
```

All of `spring-boot-starter-webmvc`/`-validation`/`-flyway`, Flyway's Postgres dialect module, and
Spock/Groovy are already in `build.gradle.kts` (see `tech.md`) — no `*.groovy` test sources or
Flyway migrations exist yet, but the dependencies are ready for the first real backend spec.

## Frontend structure

See `frontend_structure.md` for full detail.

## Naming conventions

### Backend (Java)
- **Package root**: `uk.co.stefirby.budgetsandsubscriptions.{feature}`
- **Classes**: `PascalCase` (e.g. `AccountController`, `TransactionService`)
- **Files**: match class name
- **Constants**: `SCREAMING_SNAKE_CASE`
- **Variables/Methods**: `camelCase`
- **DTOs**: suffix `Request`/`Response` (e.g. `AccountRequest`, `TransactionResponse`)
- **Specs**: `*Spec.groovy`
- **Records vs. classes**: records for small, single-shot, never-mutated-after-construction DTOs;
  classes for JPA entities, wide partial-update DTOs, and objects built incrementally across
  conditional branches — per global Java defaults.

### Frontend (TypeScript/React)
- **Files**: `PascalCase.tsx` for components, `camelCase.ts` for utilities/services
- **Types**: `PascalCase`, files under `src/types/` named `camelCase.ts`
- **Test files**: `ComponentName.test.tsx` / `fileName.test.ts`, colocated with source

## Where tests live

### Backend
Colocated: `src/test/groovy/uk/co/stefirby/budgetsandsubscriptions/{controller,service,repository,model,security,config,exception}/ClassNameSpec.groovy`.
Run with `gradlew.bat test`.

### Frontend
Colocated with source: `ComponentName.test.tsx` / `fileName.test.ts`. Run with `npm test`.

## Database

- **PostgreSQL**, local dev via Docker — own dedicated container (`docker-compose.yml`), port
  `5433`, not shared with `behavioural-activation`'s container (see `tech.md` for why).
- **Migrations**: `backend/src/main/resources/db/migration/` (Flyway) — not yet added to the
  build, no migrations exist yet.
- **JPA Entities**: `backend/src/main/java/uk/co/stefirby/budgetsandsubscriptions/model/`.

## Build artifacts

- **Backend JAR**: `backend/build/libs/*.jar`
- **Frontend build**: `frontend/dist/`

Both are git-ignored.

## Key directories

| Directory | Purpose |
|---|---|
| `.claude/steering/` | AI assistant steering files (this file, `product.md`, `tech.md`, etc.) |
| `.claude/00-initial-setup/` | Decision history — open questions, agents/steering/skills design discussion |
| `.claude/agents/` | Claude Code subagents |
| `.claude/skills/` | Claude Code skills |
| `backend/src/main/java/` | Source code |
| `backend/src/test/groovy/` | Spock test specifications |
| `frontend/src/` | React components, services, types |

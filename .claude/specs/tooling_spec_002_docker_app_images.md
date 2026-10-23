# Docker App Images (Tooling)

**Status**: Not started
**Priority**: P3 — infra/tooling, no user-facing behaviour change.
**Depends on**: None — reads, but does not depend on the completion of,
`tooling_spec_001_dev_startup_scripts.md` (that spec covers the host-native `gradlew.bat
bootRun`/`npm run dev` dev loop; this spec is the alternative fully-containerized path, and the
two don't interact).
**Area**: Tooling (repo-wide) — new `backend/Dockerfile`, `frontend/Dockerfile`,
`frontend/nginx.conf`, `docker-compose.yml` changes, a new root `.env.example`. No backend/frontend
feature code is touched, and `app.cors.allowed-origins` is deliberately left unchanged (see
Decision 2 below). Per `.claude/steering/structure.md`'s "Who writes what" table, the backend
human-hand-coded restriction applies only to `backend/`'s Java packages — it does not apply to a
Dockerfile or `docker-compose.yml`, so Claude implements this spec directly once approved.

## Overview

Today, `docker compose up` only starts Postgres (`docker-compose.yml`'s sole `postgres` service) —
the backend and frontend only ever run directly on the host (`gradlew.bat bootRun`, `npm run dev`,
or via `tooling_spec_001_dev_startup_scripts.md`'s scripts once built). This spec adds
production-style, multi-stage Dockerfiles for both the backend and frontend, and wires them into
`docker-compose.yml` as two new services (`backend`, `frontend`) alongside the existing `postgres`
service, so the whole app can be started with one `docker compose up --build`.

This mirrors the reference project `behavioural-activation`'s own
`tooling_spec_006_docker_production_images.md`, which solved the identical problem for that
project — same two-Dockerfile, reverse-proxy-not-CORS shape, adapted here to this project's actual
ports, health path, and existing `.env`.

**Out of scope**: Fly.io/Railway deployment, Tailscale configuration, TLS/HTTPS termination, CI
pipeline integration (no CI exists yet in this repo), multi-arch (`linux/arm64`) image builds,
production secrets management beyond a gitignored `.env` file consumed by Compose. These stay
future ideas, not part of this pass.

### Decision 1: backend build-stage strategy

Both `backend/gradlew` (the Unix launcher) and `backend/gradlew.bat` (Windows) are already checked
in to this repo (confirmed by listing `backend/` directly — this project never had the reference
project's stale-Windows-only-wrapper assumption to begin with, so there's no steering-doc
correction needed here the way the reference project required one).

**Decision**: the backend `Dockerfile`'s build stage is based on an `eclipse-temurin:25-jdk-alpine`
image and invokes the project's own checked-in `backend/gradlew` directly (`./gradlew build -x
test --no-daemon`) — no official `gradle:*` Docker image needed, and the exact Gradle version
always matches `backend/gradle/wrapper/gradle-wrapper.properties` by construction, since it's the
same wrapper `gradlew.bat` uses locally. Tests are not run as part of the image build (they already
run separately via `gradlew.bat test` locally, or a future CI step) — this avoids needing a
database reachable from inside the build context, and keeps image builds fast and deterministic.

### Decision 2: frontend serves via nginx, reverse-proxying `/api` — no CORS config change

`backend/src/main/resources/application.yaml`'s `app.cors.allowed-origins` is currently hardcoded
to `http://localhost:5180` with no env-var override — a gap, since a containerised frontend would
be served from a different origin (a different port, a different host once reachable remotely).

Two ways to close that gap: (a) make `app.cors.allowed-origins` env-overridable, or (b) keep
frontend and backend same-origin from the browser's point of view, by having the frontend's static
file server reverse-proxy `/api/**` to the backend container, the same way Vite's dev server
already proxies `/api` to `localhost:8090` (`frontend/vite.config.ts`).

**Decision**: option (b), matching the reference project's own resolution of the identical gap.
The frontend's production `Dockerfile` builds the static bundle (`npm run build` → `frontend/dist/`)
in a Node build stage, then serves it from an `nginx:alpine` runtime stage with a `location /api/ {
proxy_pass http://backend:8090/api/; }` block (`backend` being the Compose service's DNS name on
the Compose network), so the browser only ever talks to one origin (the frontend container's
exposed port) and `app.cors.allowed-origins` never needs to change. This mirrors the existing
dev-time proxy pattern exactly, rather than introducing a second, inconsistent mechanism
(env-configurable CORS) for the same problem. If a future deployment stage genuinely needs
cross-origin access, making `app.cors.allowed-origins` configurable can be revisited then — not
pre-built speculatively here.

## Requirement 1: Backend production Dockerfile

**User story**: As the person running this app, I want a Docker image that runs the Spring Boot
backend without needing Java or Gradle installed on the host.

### TOOLING-002-AC-01 [AUTO]: Backend image builds successfully
**Statement**: `backend/Dockerfile` shall define a multi-stage build: a build stage based on
`eclipse-temurin:25-jdk-alpine` running the project's own checked-in `./gradlew build -x test
--no-daemon` (per Decision 1), and a runtime stage based on `eclipse-temurin:25-jre-alpine` that
copies only the built jar (`backend/build/libs/*.jar`) from the build stage, installs `curl` (for
the Compose healthcheck in `TOOLING-002-AC-05`), and runs the jar via `java -jar`. Running `docker
build -t budgets-backend ./backend` shall exit `0` and produce a runnable image.

**Rationale**: A multi-stage build keeps the final runtime image small (no JDK/Gradle-wrapper-cache
left in the shipped layer) while still producing output equivalent to `gradlew.bat build` locally,
since both invoke the same wrapper-pinned Gradle version.

**References**:
- New file: `backend/Dockerfile`
- New file: `backend/.dockerignore` (excludes `build/`, `.gradle/`, `logs/`, `*.log`, local `.env*`
  files — so the build context doesn't ship host build artifacts into the image)
- `backend/gradlew`, `backend/gradle/wrapper/` (the checked-in wrapper this stage invokes directly)
- `backend/build.gradle.kts` (Java 25 toolchain, Spring Boot 4.1.1)

**Test Case (Green)**: `docker build -t budgets-backend ./backend` from the repo root exits `0`;
`docker run --rm budgets-backend java -version` (or equivalent smoke check) confirms a Java 25
runtime inside the image.

### TOOLING-002-AC-02 [AUTO]: Backend image runs and serves traffic using only env-var config
**Statement**: The backend image shall start the Spring Boot application using only environment
variables already supported by `backend/src/main/resources/application.yaml` (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`) — no new Spring property or code change required
to run inside a container.

**Rationale**: All the config the backend needs to run in Compose already exists and is
externalised — this AC is a smoke test that containerising doesn't need to add any new one, rather
than new functionality.

**References**: `backend/src/main/resources/application.yaml`

**Test Case (Green)**: run the image with `DB_URL` pointed at a reachable Postgres instance and
`JWT_SECRET_KEY` set; `GET /api/auth/me` against the mapped port returns `401` (the same "ready"
signal `tooling_spec_001_dev_startup_scripts.md`'s `start-dev.sh` already uses for the host-native
backend), confirming the app started successfully and is serving HTTP.

## Requirement 2: Frontend production Dockerfile

**User story**: As the person running this app, I want a Docker image that serves the built
frontend without needing Node installed on the host, and without needing to separately configure
CORS to let it talk to the backend.

### TOOLING-002-AC-03 [AUTO]: Frontend image builds successfully
**Statement**: `frontend/Dockerfile` shall define a multi-stage build: a Node build stage running
`npm ci` then `npm run build` (producing `frontend/dist/`), and an `nginx:alpine` runtime stage that
copies `dist/` into nginx's web root plus a project-provided `frontend/nginx.conf`. Running `docker
build -t budgets-frontend ./frontend` shall exit `0` and produce a runnable image.

**References**:
- New file: `frontend/Dockerfile`
- New file: `frontend/.dockerignore` (excludes `node_modules/`, `dist/`, `coverage/`, local
  `.env*` files)
- New file: `frontend/nginx.conf`

**Test Case (Green)**: `docker build -t budgets-frontend ./frontend` from the repo root exits `0`.

### TOOLING-002-AC-04 [AUTO]: Frontend container serves the SPA and proxies `/api` same-origin
**Statement**: The frontend container's nginx config shall (a) serve `frontend/dist/`'s static
files, falling back to `index.html` for client-side routed paths (matching `react-router-dom`'s
history-mode routing already in use for `/login`, `/register`, and the protected root route), and
(b) proxy any request under `/api/` to `http://backend:8090/api/` (the `backend` Compose service's
DNS name), unchanged path otherwise. No CORS configuration is added anywhere — this AC is satisfied
precisely because the browser only ever sees one origin (the frontend container's own exposed
port), per Decision 2.

**Rationale**: Mirrors `frontend/vite.config.ts`'s existing dev-time proxy (`'/api': { target:
'http://localhost:8090' }`) at the production layer, so the frontend's existing `authApi`/`client.ts`
relative-path behaviour needs no change between dev and containerised-prod.

**References**:
- `frontend/vite.config.ts` (the dev-time proxy this mirrors)
- `frontend/src/services/client.ts`, `frontend/src/services/authApi.ts` (already relative-path/
  env-base-friendly, per `frontend_spec_001_basic_login.md`'s implementation)

**Test Case (Green)**: with both containers running via Compose, `curl
http://localhost:<frontend-port>/` returns the SPA's `index.html`; `curl
http://localhost:<frontend-port>/api/auth/me` returns the backend's `401` response (proving the
proxy reaches the backend container), and a client-side route like `/login` or `/register` also
returns `index.html` (not nginx's default 404) on a direct request/refresh.

## Requirement 3: `docker-compose.yml` runs the whole app

**User story**: As the person running this app, I want one `docker compose up` to start Postgres,
the backend, and the frontend together, instead of starting Postgres via Compose and the other two
manually (or via `tooling_spec_001_dev_startup_scripts.md`'s scripts).

### TOOLING-002-AC-05 [AUTO]: `backend` and `frontend` services added to `docker-compose.yml`
**Statement**: `docker-compose.yml` shall gain a `backend` service (built from `backend/Dockerfile`,
`depends_on: postgres` with `condition: service_healthy`, `DB_URL` pointing at the `postgres`
service's Compose DNS name — `jdbc:postgresql://postgres:5432/budgetsandsubscriptions` — rather
than `localhost:5433`, `JWT_SECRET_KEY` sourced from `env_file: .env` rather than inlined, and a
healthcheck hitting `/api/auth/me`) and a `frontend` service (built from `frontend/Dockerfile`,
`depends_on: backend` with `condition: service_healthy`, exposing a mapped port for host access).
Running `docker compose up --build` from the repo root shall bring up all three services, with the
frontend reachable from the host browser and able to register/log in/log out end-to-end.

**Rationale**: This is the actual deliverable the Overview promises — the two new Dockerfiles alone
don't start the app together without this wiring.

**References**:
- `docker-compose.yml` (existing `postgres` service, to extend — note the container-network port is
  Postgres's own default `5432`, not the host-mapped `5433` the backend's *local, non-containerised*
  `application.yaml` default points at; the `backend` service's `DB_URL` must use `5432`)
- New file: root `.env.example` (documents `JWT_SECRET_KEY`, consistent with the existing gitignored
  root `.env`'s single entry today — extend it if further Compose-level secrets are added later)

**Test Case (Green)**: `docker compose up --build -d` from the repo root; `docker compose ps` shows
all three services healthy/running; opening the mapped frontend port in a browser allows
registering a new account and logging in/out end-to-end (manual smoke test — see `[MANUAL]` note
below). `docker compose down` cleanly stops all three.

**Verification**: `[MANUAL]` for the end-to-end register/login/logout smoke test (no automated
integration test harness spins up full Compose in this repo); `[AUTO]`-verifiable mechanically via
`docker compose config` validating the compose file parses and `docker compose up --build -d`
exiting `0`.

### TOOLING-002-AC-06 [MANUAL]: No secret is baked into an image layer
**Statement**: Neither `backend/Dockerfile` nor `frontend/Dockerfile` shall `COPY` or `ARG`/`ENV`
any real credential (DB password, JWT signing secret) into an image layer. All such values shall be
supplied only at container-run time via `docker-compose.yml`'s `environment`/`env_file`, sourced
from the gitignored root `.env`.

**Rationale**: Matches the project's existing "never commit secrets" rule
(`.claude/steering/tech.md`'s Secrets section) — a value baked into an image layer is recoverable
from the image itself (`docker history`/layer inspection) even if never committed to git, which is a
distinct risk from a value merely absent from source control.

**References**: repo-root `.gitignore`, `backend/Dockerfile`, `frontend/Dockerfile`,
`docker-compose.yml`

**Verification**: `[MANUAL]` — review both Dockerfiles for any `ARG`/`ENV` line naming a credential,
and confirm `docker history budgets-backend`/`budgets-frontend` shows no secret value in any layer
command.

## Cross-references

| Reference | What it provides |
|---|---|
| `docker-compose.yml` | Existing `postgres` service; extended with `backend`/`frontend` |
| `backend/build.gradle.kts` | Java 25 toolchain, Spring Boot 4.1.1, jar output location |
| `backend/gradlew`, `backend/gradle/wrapper/` | Checked-in wrapper the build-stage image invokes directly |
| `backend/src/main/resources/application.yaml` | Env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `app.cors.allowed-origins`) the backend container is configured through |
| `frontend/vite.config.ts` | Dev-time `/api` proxy this spec's nginx config mirrors in prod |
| `frontend/package.json` | `npm run build` → `frontend/dist/` |
| `frontend_spec_001_basic_login.md` | Confirms the frontend's API layer (`client.ts`/`authApi.ts`) is already relative-path-friendly, needing no change for the container proxy to work |
| `backend_spec_001_basic_login.md` (`AUTH-001-AC-15`) | Why `/api/auth/me` returning `401` is a valid Compose healthcheck/readiness signal |
| Repo-root `.env` (gitignored) | Existing `JWT_SECRET_KEY` — `.env.example` documents its shape for Compose |
| `.claude/steering/tech.md` | Secrets rules; Hosting section |
| `tooling_spec_001_dev_startup_scripts.md` | The alternative host-native dev workflow this spec doesn't modify or depend on |

## Acceptance Criteria Summary

- [ ] TOOLING-002-AC-01 [AUTO]: `backend/Dockerfile` multi-stage build succeeds via the checked-in `gradlew`, JRE 25 runtime
- [ ] TOOLING-002-AC-02 [AUTO]: Backend image runs using only existing env-var config, no code/property change needed
- [ ] TOOLING-002-AC-03 [AUTO]: `frontend/Dockerfile` multi-stage build succeeds
- [ ] TOOLING-002-AC-04 [AUTO]: Frontend container serves the SPA with history-mode fallback and proxies `/api` same-origin, no CORS change
- [ ] TOOLING-002-AC-05 [AUTO]: `docker-compose.yml` gains `backend`/`frontend` services; `docker compose up --build` runs the whole app
- [ ] TOOLING-002-AC-06 [MANUAL]: No secret is baked into an image layer; all sourced from the gitignored root `.env` at container-run time

## Test case sketches

Like `tooling_spec_001_dev_startup_scripts.md`, this is infra/tooling with no backend-Java/frontend-TS
test harness to wire these into — verification is a one-time build/run/curl recipe Claude performs
during implementation and reports the result of (the "Test Case (Green)" sections above are the
authoritative version of each; the following consolidates them into a run order).

```
1. docker build -t budgets-backend ./backend          # TOOLING-002-AC-01
2. docker build -t budgets-frontend ./frontend         # TOOLING-002-AC-03
3. docker compose up --build -d                        # TOOLING-002-AC-05
4. docker compose ps                                    # all three services healthy
5. curl http://localhost:<frontend-port>/               # index.html
6. curl http://localhost:<frontend-port>/api/auth/me     # 401, proves proxy reaches backend
7. curl http://localhost:<frontend-port>/login           # index.html, not a 404 (SPA fallback)
8. Manual: open the mapped frontend port in a browser, register a new account, confirm redirect
   into the app, log out, confirm redirect to /login.                      # TOOLING-002-AC-04/05
9. docker history budgets-backend / budgets-frontend     # no secret value in any layer command
   # TOOLING-002-AC-06
10. docker compose down                                  # clean stop of all three
```

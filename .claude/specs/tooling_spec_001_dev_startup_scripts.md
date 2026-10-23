# Dev Startup Scripts (Tooling)

**Status**: Not started
**Priority**: P2 — quality-of-life for local development; nothing else depends on this existing,
but every manual `gradlew.bat bootRun` / `npm run dev` session benefits once it does.
**Depends on**: None.
**Area**: Tooling (repo-wide) — shell scripts and their lib helpers under `scripts/`, no
backend/frontend feature code touched. Per `.claude/steering/structure.md`'s "Who writes what"
table, the backend human-hand-coded restriction applies only to `backend/`'s Java packages
(`model/`, `repository/`, `service/`, `controller/`, `security/`, `exception/`, `config/` with real
content) — it does not apply to repo-root tooling scripts, so Claude implements this spec directly
once approved, the same way frontend specs are implemented directly.

## Overview

Today, running the app locally means manually starting Postgres (`docker compose up -d`), then
opening two more terminals for `cd backend && gradlew.bat bootRun` and `cd frontend && npm run
dev`, and manually hunting down and killing processes to stop either one. This spec adds three
scripts — `start-dev.sh`, `stop-dev.sh`, `restart-dev.sh` — plus two shared library files
(`scripts/lib/dev-common.sh`, `scripts/lib/docker-common.sh`) that do this reliably on Windows via
git-bash, modeled directly on the reference project `behavioural-activation`'s own
`scripts/start-dev.sh`/`stop-dev.sh`/`restart-dev.sh`/`lib/dev-common.sh`/`lib/docker-common.sh`,
adapted to this project's actual ports, health endpoints, and `.env` contents.

Each script accepts an optional `backend`/`frontend` target (default: both) and a `--debug` flag
(backend only — attaches `gradlew.bat bootRun --debug-jvm` on a fixed JDWP port). Idempotency and
safety are the core design goals, carried over directly from the reference project: starting an
already-running service is a no-op (by port check), and stopping only ever kills a PID that both
owns the expected port *and* whose process image matches what's expected (`java.exe`/`node.exe`) —
never a PID based on a possibly-stale `.pid` file alone.

**Out of scope**: this spec does not touch `docker-compose.yml` beyond reading its existing
`postgres` service for the preflight check — adding `backend`/`frontend` Compose services is
`tooling_spec_002_docker_app_images.md`'s job. A production/CI invocation of these scripts is not
a goal; they are a local, interactive, git-bash-on-Windows convenience only.

## Requirement 1: `start-dev.sh` starts the backend and/or frontend

**User story**: As the developer, I want one command that starts whichever of the backend/frontend
isn't already running, waits until each is actually ready, and tells me where its logs are — so I
don't have to juggle terminals or guess whether a server is up yet.

### TOOLING-001-AC-01 [AUTO]: Already-running target is skipped, not restarted
**Statement**: When `start-dev.sh` is invoked for a target whose port
(backend `8090`, frontend `5180`) already has a `LISTENING` owner, it shall print that the service
is already running (including the owning PID when resolvable) and exit `0` without starting a new
process.

**Rationale**: Mirrors the reference project's own idempotency — re-running `start-dev.sh` after
it's already up should be harmless, not spawn a second competing process on the same port.

### TOOLING-001-AC-02 [AUTO]: Backend start is gated on a Docker/Postgres preflight check
**Statement**: Before starting the backend, `start-dev.sh` shall verify (via
`lib/docker-common.sh`'s `docker_preflight`) that the Docker daemon is reachable and the
`docker-compose.yml` `postgres` service reports a `healthy` status; if either check fails, it shall
print an actionable message (start Docker Desktop, or run `docker compose up -d`) and exit non-zero
without invoking `gradlew.bat bootRun`.

**Rationale**: Starting the backend against a Postgres that isn't up yet wastes the full
backend-startup timeout before failing with a confusing connection error — failing fast here, same
as the reference project, surfaces the real cause immediately.

### TOOLING-001-AC-03 [AUTO]: Backend/frontend start in the background with output captured to `logs/`
**Statement**: When starting the backend or frontend, `start-dev.sh` shall launch `gradlew.bat
bootRun` (from `backend/`, with `--debug-jvm` appended when `--debug` is passed) or `npm run dev`
(from `frontend/`) respectively as a background process, redirecting its combined stdout/stderr to
`logs/backend.log` / `logs/frontend.log` (creating `logs/` if absent).

**Rationale**: `logs/` is already gitignored in this repo — background launches need their output
captured somewhere inspectable rather than lost, and a foreground launch would block the script
from reaching its own health-check/PID-recording step.

### TOOLING-001-AC-04 [AUTO]: Startup blocks until the service is confirmed ready, or times out
**Statement**: After launching a target, `start-dev.sh` shall poll its health URL
(backend `http://localhost:8090/api/auth/me`, frontend `http://localhost:5180/`) every 2 seconds
for up to a target-specific timeout (backend 90s, frontend 20s) until any HTTP response is
received (not necessarily `2xx` — the backend's `/api/auth/me` is expected to return `401`
unauthenticated, which still proves the server is up and routing requests); if the timeout elapses
first, it shall print the last 20 lines of that target's log file and exit non-zero.

**Rationale**: `/api/auth/me` is gated by `SecurityConfig`'s `.authenticated()` rule
(`backend_spec_001_basic_login.md`'s `AUTH-001-AC-15`) — unlike a public health endpoint, "ready"
here means "got any response at all," exactly as the reference project's own `wait_for_health`
treats its equivalent auth-gated endpoint. Printing recent log lines on timeout gives an immediate
diagnostic instead of a bare "timed out."

### TOOLING-001-AC-05 [AUTO]: A ready service's owning PID is recorded
**Statement**: Once a target's health check passes, `start-dev.sh` shall resolve the real
port-owner PID (via `netstat`) and write it to `logs/backend.pid` / `logs/frontend.pid`.

**Rationale**: The recorded PID is advisory only (`stop-dev.sh` never trusts it blindly — see
`TOOLING-001-AC-06`), but it's useful for a human glancing at the file, and gives `stop-dev.sh`'s
"nothing listening" fallback path something to clean up.

### TOOLING-001-AC-06 [AUTO]: `.env` is loaded before the backend starts, without overriding an already-set variable
**Statement**: Before starting the backend, `start-dev.sh` shall export every `KEY=VALUE` line from
a repo-root `.env` file (if present, skipping blank lines and `#` comments) into its own process
environment, except for any variable already set in the invoking shell's environment, which it
shall leave untouched.

**Rationale**: The repo-root `.env` already carries `JWT_SECRET_KEY` — without loading it,
`gradlew.bat bootRun` would start with no signing key and fail. Not overriding an already-set
variable means `JWT_SECRET_KEY=x bash scripts/start-dev.sh` still wins over `.env` for an ad hoc
override, matching the reference project's own `load_dotenv` behaviour.

## Requirement 2: `stop-dev.sh` stops the backend and/or frontend safely

**User story**: As the developer, I want to stop a running dev server without accidentally killing
some unrelated process that happens to be using the same port, or being blocked by a stale PID file
left over from a crashed session.

### TOOLING-001-AC-07 [AUTO]: Nothing listening is treated as already-stopped
**Statement**: When `stop-dev.sh` is invoked for a target whose port has no `LISTENING` owner, it
shall print that the service isn't running, remove any stale `.pid` file for that target, and exit
`0`.

**Rationale**: Stopping an already-stopped service should be a harmless no-op, consistent with
`start-dev.sh`'s own idempotency in `TOOLING-001-AC-01`.

### TOOLING-001-AC-08 [AUTO]: The live port-owner PID is re-resolved, never trusted from the `.pid` file alone
**Statement**: When a target's port has a `LISTENING` owner, `stop-dev.sh` shall re-resolve the
current owning PID via `netstat` rather than reading it from `logs/*.pid`, before taking any kill
action.

**Rationale**: A saved PID can go stale (the original process died and a different one now owns
the port, or vice versa) — re-resolving from live system state every time, exactly as the reference
project does, avoids ever killing the wrong process based on cached data.

### TOOLING-001-AC-09 [AUTO]: A kill is refused if the resolved PID's process image doesn't match expectations
**Statement**: If the re-resolved PID's process image (via `tasklist`) is not the expected one for
that target (`java.exe` for backend, `node.exe` for frontend), `stop-dev.sh` shall refuse to kill
it, print the mismatched PID/image along with the manual `tasklist`/`taskkill` commands needed to
investigate and resolve it by hand, and exit non-zero.

**Rationale**: The core safety property this script exists for — never kill a process just because
it happens to be bound to the expected port, since something unrelated could have claimed it after
a previous session's dev server exited uncleanly.

### TOOLING-001-AC-10 [AUTO]: A verified PID is killed and its `.pid` file removed
**Statement**: If the re-resolved PID's process image matches what's expected, `stop-dev.sh` shall
kill that PID and its process tree, remove that target's `.pid` file, and print confirmation
including which PID/port was stopped.

**Rationale**: The positive-path completion of the safety check in `TOOLING-001-AC-09`.

## Requirement 3: `restart-dev.sh` restarts the backend and/or frontend

**User story**: As the developer, I want a single command that stops then starts a target, so I
don't have to run two scripts back-to-back after, say, pulling new backend code.

### TOOLING-001-AC-11 [AUTO]: Restart runs stop then start, reporting failure from either step
**Statement**: When `restart-dev.sh` is invoked for a target, it shall invoke `stop-dev.sh` with
the same arguments, then `start-dev.sh` with the same arguments, and shall exit non-zero if either
step exited non-zero — even if the later step succeeded.

**Rationale**: A naive shell script that only checks the last command's exit code would silently
hide a `stop-dev.sh` failure if `start-dev.sh` subsequently happened to succeed (e.g. against a
process that was never actually stopped) — both exit codes must be captured independently.

### TOOLING-001-AC-12 [AUTO]: Nested stop/start invocations don't each pause on failure
**Statement**: When `restart-dev.sh` invokes `stop-dev.sh`/`start-dev.sh` as subprocesses, it shall
set `DEV_SCRIPT_NESTED=1` scoped to each nested invocation only (not exported into `restart-dev.sh`'s
own shell), so that only `restart-dev.sh`'s own top-level failure pauses for a keypress
(`TOOLING-001-AC-13`), not each nested script's own trap as well.

**Rationale**: Without this scoping, a single underlying failure could trigger the keypress pause
up to three times (each nested script's own `EXIT` trap, then `restart-dev.sh`'s own) — confusing
and slow to click through. Scoping `DEV_SCRIPT_NESTED` per-invocation rather than `export`ing it
also ensures `restart-dev.sh`'s *own* later `EXIT` trap still fires normally, rather than
accidentally inheriting the suppression meant only for its children.

## Requirement 4: All three scripts pause before an auto-spawned window closes on failure

**User story**: As the developer who sometimes double-clicks one of these scripts (or launches it
from a file association) rather than running it from an already-open terminal, I want to actually
see the failure message before the window disappears, instead of it flashing shut instantly.

### TOOLING-001-AC-13 [AUTO]: A non-zero exit pauses for a keypress, only when not nested and interactive
**Statement**: Each of `start-dev.sh`, `stop-dev.sh`, and `restart-dev.sh` shall register an `EXIT`
trap that, only when the script's own exit status is non-zero AND `DEV_SCRIPT_NESTED` is unset AND
stdin is a TTY, prints a "press any key to close" prompt and blocks on a single keypress before
the script actually exits.

**Rationale**: Covers the disposable-window case (double-click, or launched via a Windows file
association) without ever blocking a future non-interactive/CI invocation — the `[ -t 0 ]` TTY
check means a script run with no attached terminal exits immediately on failure rather than hanging
forever waiting for input nobody can supply. The `DEV_SCRIPT_NESTED` check is what makes
`TOOLING-001-AC-12` actually work.

## Cross-references

| Reference | What it provides |
|---|---|
| `backend/src/main/resources/application.yaml` | Backend port (`8090`), confirms `JWT_SECRET_KEY` env var name |
| `backend_spec_001_basic_login.md` (`AUTH-001-AC-15`) | Why `/api/auth/me` returning `401` still counts as "ready" |
| `frontend/vite.config.ts` | Frontend port (`5180`, `strictPort: true`) |
| `docker-compose.yml` (repo root) | The existing `postgres` service `docker_preflight` checks against |
| Repo-root `.env` (gitignored) | `JWT_SECRET_KEY` — what `load_dotenv` sources before backend start |
| Repo-root `.gitignore` | Confirms `logs/` is already ignored — no change needed there |
| `behavioural-activation/scripts/*.sh`, `scripts/lib/*.sh` | Reference implementation this spec adapts (ports/health URLs/`.env` contents differ; mechanisms carried over directly) |
| `tooling_spec_002_docker_app_images.md` (future) | Adds `backend`/`frontend` services to `docker-compose.yml` — out of scope here |

## Acceptance Criteria Summary

- [ ] TOOLING-001-AC-01 [AUTO]: Already-running target is skipped, not restarted
- [ ] TOOLING-001-AC-02 [AUTO]: Backend start gated on a Docker/Postgres preflight check
- [ ] TOOLING-001-AC-03 [AUTO]: Backend/frontend start in the background, output captured to `logs/`
- [ ] TOOLING-001-AC-04 [AUTO]: Startup blocks until health-checked ready, or times out with log tail
- [ ] TOOLING-001-AC-05 [AUTO]: A ready service's owning PID is recorded to `logs/*.pid`
- [ ] TOOLING-001-AC-06 [AUTO]: `.env` loaded before backend start, without overriding already-set vars
- [ ] TOOLING-001-AC-07 [AUTO]: Nothing listening is treated as already-stopped
- [ ] TOOLING-001-AC-08 [AUTO]: Live port-owner PID is re-resolved, never trusted from `.pid` file alone
- [ ] TOOLING-001-AC-09 [AUTO]: A kill is refused if the resolved PID's process image doesn't match
- [ ] TOOLING-001-AC-10 [AUTO]: A verified PID is killed and its `.pid` file removed
- [ ] TOOLING-001-AC-11 [AUTO]: Restart runs stop then start, reporting failure from either step
- [ ] TOOLING-001-AC-12 [AUTO]: Nested stop/start invocations don't each pause on failure
- [ ] TOOLING-001-AC-13 [AUTO]: A non-zero exit pauses for a keypress, only when not nested and interactive

## Test case sketches

These scripts aren't backend Java/Spock or frontend Vitest code, so there's no automated test
harness to wire into — verification is manual/scripted shell invocation, matching how the reference
project verified its own equivalent scripts. Each sketch below is a `[MANUAL]` smoke-test recipe
Claude runs once during implementation and reports the result of, not a committed automated test.

```
TOOLING-001-AC-01: with the backend already running (confirm via `netstat`), run
`bash scripts/start-dev.sh backend` — expect "already running" message, exit 0, no second process
spawned (verify PID unchanged).
```

```
TOOLING-001-AC-02: with Docker Desktop stopped, run `bash scripts/start-dev.sh backend` — expect
an actionable "start Docker Desktop" message and non-zero exit, no gradlew process spawned.
Repeat with Docker running but `docker compose up -d` not yet run — expect a
"run docker compose up -d" message instead.
```

```
TOOLING-001-AC-03/04/05: with Docker+Postgres up and nothing else running, run
`bash scripts/start-dev.sh` (both) — expect `logs/backend.log`/`logs/frontend.log` to exist and
grow, both health checks to pass within their timeouts, `logs/backend.pid`/`logs/frontend.pid` to
be written with PIDs that `tasklist` confirms are `java.exe`/`node.exe`.
```

```
TOOLING-001-AC-06: with `JWT_SECRET_KEY` unset in the invoking shell and present only in `.env`,
run `bash scripts/start-dev.sh backend` — expect the backend to start successfully (proving the
key was loaded). Then run `JWT_SECRET_KEY=override-value bash scripts/start-dev.sh backend` with
the backend already stopped — expect the shell-provided value to win (observable via a temporary
log line, removed after verification).
```

```
TOOLING-001-AC-07/08/09/10: start the backend, then manually run a decoy process bound to port
8090 instead... — in practice, simulate AC-09 by temporarily asserting the expected image check
against a PID known to be, e.g., `explorer.exe` and confirming `stop-dev.sh` refuses it; confirm
AC-10 with a real `gradlew.bat bootRun`-started backend, verifying `stop-dev.sh backend` kills it
and `logs/backend.pid` is removed.
```

```
TOOLING-001-AC-11/12: with both targets running, run `bash scripts/restart-dev.sh` — expect both
to stop then restart, a single pause-on-failure prompt (not three) if a deliberately-broken build
is substituted temporarily to force a failure, and the normal case exits 0 with both targets
healthy again.
```

```
TOOLING-001-AC-13: run any script non-interactively (e.g. `bash scripts/stop-dev.sh backend
</dev/null`) against a forced failure condition — expect immediate non-zero exit, no hang.
Separately, run the same script from an interactive terminal against the same forced failure —
expect the "press any key" prompt to appear and block until a key is pressed.
```

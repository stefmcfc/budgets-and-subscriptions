# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.7.0] - 2026-10-26

### Added
- `Account` JPA entity and the `V002__create_accounts_table.sql` Flyway migration, backing the
  in-progress accounts feature (`backend_spec_002_accounts.md`): required `bankName`/`accountName`
  (split so accounts can later be grouped by bank), `type` restricted to `CURRENT`/`SAVINGS`/
  `CASH_ISA`, a required owner, and `balance` seeded from `openingBalance` at construction
  (`BACKEND-002-AC-01` through `AC-06`).
- `POST /api/accounts`: creates an account owned by the authenticated user, with the owner derived
  solely from the JWT principal rather than any owner/user-id field in the request body
  (`BACKEND-002-AC-07` through `AC-09`, `AC-11`). `type` is restricted to the allowed enum values
  and `openedOn` to today or earlier via `@PastOrPresent` (`AC-10`, `AC-12`). Also adds a
  `GlobalExceptionHandler` handler for `HttpMessageNotReadableException`, so a malformed request
  body or an unrecognised `type` string returns `400` instead of falling through to the generic
  `500` handler.

## [0.6.0] - 2026-10-23

### Added
- `README.md` (`tooling_spec_003_repo_conventions.md`): what the app is, getting started (manual
  and scripted run paths), local ports, and links out to `.claude/steering/`/`CHANGELOG.md` rather
  than duplicating them.
- `.editorconfig`, derived from this repo's actual existing indentation (4-space Java/Kotlin,
  2-space TS/YAML/CSS) so adopting it produces zero diff noise on any existing file.
- `.github/pull_request_template.md`: a lightweight checklist (tests, `CHANGELOG` entry, migration
  if relevant) scoped to what this repo's process actually has.
- `CLAUDE.md` now documents the Conventional Commits convention and tagging each release's merge
  commit with an annotated git tag.
- `server.shutdown: graceful` in `application.yaml` — idiomatic Spring Boot practice for shutdown
  paths that give the JVM a normal signal/hook invocation (e.g. a future `docker stop`). Found
  during implementation that this does *not* help `scripts/stop-dev.sh`'s `taskkill` on this
  machine — that process can only be force-killed, which skips JVM shutdown hooks regardless of
  this setting; documented honestly rather than left as an inaccurate claim.

## [0.5.0] - 2026-10-23

### Added
- `scripts/start-dev.sh`, `scripts/stop-dev.sh`, `scripts/restart-dev.sh` (`tooling_spec_001_dev_startup_scripts.md`):
  start/stop/restart the backend and/or frontend dev servers in the background, idempotently,
  with output captured to `logs/`. Starting the backend is gated on a Docker/Postgres preflight
  check; stopping always re-resolves the live port-owner PID and refuses to kill a process whose
  image doesn't match what's expected (`java.exe`/`node.exe`), rather than trusting a possibly
  stale `.pid` file. Both scripts load `JWT_SECRET_KEY` from the repo-root `.env` before starting
  the backend, and pause on a keypress before an auto-spawned window closes on failure
  (TOOLING-001-AC-01 through AC-13).

## [0.4.0] - 2026-10-23

### Added
- Frontend half of stage 1 authentication (`frontend_spec_001_basic_login.md`): `RegisterForm`
  and `LoginForm` (accessible labeled fields, sign-up/sign-in autofill attributes, client-side
  validation on registration, pending/error states), an `AuthProvider`/`useAuth` context that
  resolves the session once via `GET /api/auth/me` on load without persisting anything to
  browser storage, a `ProtectedRoute` guard with a distinct loading state so no protected content
  flashes before the session check resolves, and a logout action — plus the `authApi` service
  layer and shared `request<T>()`/`ApiError` wrapper (`src/services/client.ts`) all backend calls
  now go through (AUTH-002-AC-01 through AC-22).
- Routing (`react-router-dom`): `/login`, `/register`, and a `/` route guarded by
  `ProtectedRoute`, wired up in `App.tsx`.

### Fixed
- `frontend/src/test-setup.ts` now imports `@testing-library/jest-dom/vitest` instead of the
  plain `@testing-library/jest-dom` entry point — the installed `jest-dom` v7 only type-augments
  Vitest's `expect` via that subpath, so `tsc -b` (and therefore `npm run build`) failed on every
  jest-dom matcher used in a test file even though `vitest run` itself passed.

## [0.3.0] - 2026-10-23

### Added
- `POST /api/auth/login`: authenticates an existing user against their stored BCrypt hash and
  issues the same signed HS256 JWT access-token cookie registration does, returning `200` with the
  user's id/email/displayName/role — the response body never includes the password hash or the raw
  token (AUTH-001-AC-10, AC-12, AC-13).
- Failed login (unknown email, wrong password, or a blank/missing email or password) responds
  uniformly, without revealing which credential was wrong; login normalizes email to lowercase
  before lookup, matching registration (AUTH-001-AC-11, AC-18, AC-19).
- `GET /api/auth/me`: returns the current user's id/email/displayName/role for a valid session,
  `401` for a missing, malformed, expired, or invalid-signature one (AUTH-001-AC-14, AC-15).
- `JwtAuthenticationFilter`: populates Spring Security's `SecurityContextHolder` from the
  `access_token` cookie ahead of the authorization check, so `.authenticated()`-gated routes work
  off real Spring Security authentication rather than ad hoc controller-side cookie parsing; a
  missing cookie leaves the request unauthenticated rather than rejected outright (so the
  `permitAll` login/register routes are unaffected), and a malformed/expired/invalid-signature
  cookie is caught rather than surfacing as a `500` (AUTH-001-AC-20 through AC-23).
- `POST /api/auth/logout`: clears the access-token cookie with an immediately-expiring `Set-Cookie`
  and responds `200`, with no authentication required to call it; the cleared cookie is honoured
  immediately, so a subsequent `GET /api/auth/me` with no cookie present responds `401`
  (AUTH-001-AC-16, AC-17).

### Fixed
- `GlobalExceptionHandler`'s `400` validation-error response now returns the real per-field
  messages instead of a Java object's identity hash.

## [0.2.0] - 2026-10-14

### Added
- `User` JPA entity and first Flyway migration (`V001__create_users_table.sql`), backing the
  in-progress authentication feature: case-insensitive unique email, required display name,
  role defaulting to `USER`.
- `POST /api/auth/register`: creates a new `USER`-role account with a BCrypt-hashed password,
  rejects a duplicate email (case-insensitive) with `409` and a too-short password or malformed
  email/display name with `400`, and issues a signed HS256 JWT access-token cookie on success —
  the response body never includes the password, the password hash, or the raw token
  (AUTH-001-AC-05 through AC-09).
- Spring Security configuration (public `/api/auth/register` and `/api/auth/login`, everything
  else under `/api/**` authenticated, CSRF disabled in favour of `SameSite=Strict` cookies), a
  CORS allow-list scoped to `/api/**`, a JSON authentication entry point for `401` responses, and
  a global exception handler mapping duplicate-email and validation failures to the right status.

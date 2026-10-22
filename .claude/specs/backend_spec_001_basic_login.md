# Basic Login (Backend)

**Status**: In progress
**Priority**: P0 — build order step 1 ("simplest secure version first"); every other backend
feature depends on authenticated requests existing.
**Depends on**: None — first backend spec in the project.
**Area**: Backend

## Overview

This delivers the first, deliberately minimal pass at authentication: a `User` entity (with its
`role` column present from the start, even though nothing acts on `ADMIN` differently yet),
registration and login that each issue a single short-lived access-token cookie (registration
auto-logs the new user in, rather than requiring a separate login call), a session-rehydration
endpoint (`GET /api/auth/me`) so the frontend can answer "who's logged in" after a page refresh,
and a cookie-clearing logout. It deliberately excludes refresh tokens (no rotation, no reuse
detection, no server-side revocation) and anything admin-specific — those are stages 2 and 3,
tracked as separate specs per `.claude/01-authentication/design-decisions.md`'s "Spec breakdown."
The goal, per the project's build order, is to trace one request through the whole stack — login
form to database storage — before adding any more moving parts.

## Requirements

### Requirement 1: `User` entity and schema

**User story**: As the system, I need a durable, uniquely-identified user record so that
registration and login have something to create and verify credentials against.

#### AUTH-001-AC-01 [AUTO]: Case-insensitive unique email
**Statement**: The `User` entity shall enforce a case-insensitive unique constraint on `email`,
such that persisting a second user whose email differs from an existing one only by case fails.

**Rationale**: Design decision (`design-decisions.md`'s "Registration / login identifier") — email
is the sole login identifier; `Foo@x.com` and `foo@x.com` must collide.

**Addendum (found during AUTH-001-AC-10/AC-11 code review)**: this constraint alone doesn't make
*lookups* case-insensitive — see `AUTH-001-AC-18`, which normalises email at the `AuthService`
boundary so registration and login both honour this guarantee directly, rather than relying solely
on the DB-level functional index below.

#### AUTH-001-AC-02 [AUTO]: Role defaults to USER
**Statement**: The `User` entity shall default `role` to `USER` when no role is explicitly set at
creation.

**Rationale**: Every registration in this spec produces a `USER`; there's no in-app path to create
an `ADMIN` account (that's a manual one-time DB update, out of scope here per
`design-decisions.md`'s "Admin bootstrap"). The column exists now only so the access token's role
claim has somewhere to read from.

#### AUTH-001-AC-03 [AUTO]: `displayName` is required
**Statement**: The `User` entity shall reject persistence of a user with a null or blank
`displayName`.

**Rationale**: `design-decisions.md`'s "Display name" section — a single required free-text field,
captured at registration.

#### AUTH-001-AC-04 [AUTO]: Schema matches entity mapping
**Statement**: When the Spring application context loads, the `users` table created by its Flyway
migration shall match the `User` entity's JPA mapping, such that startup succeeds under
`ddl-auto: validate`.

**Rationale**: Per `tech.md`, schema changes must come from a Flyway migration, not Hibernate
auto-DDL; `ddl-auto: validate` is already the enforcement mechanism, verifiable by the application
context actually starting.

### Requirement 2: Registration

**User story**: As a new user, I want to register with an email, password, and display name so
that I have an account to log into the app with.

#### AUTH-001-AC-05 [AUTO]: Successful registration auto-logs the user in
**Statement**: When `POST /api/auth/register` is requested with a syntactically valid email not
already in use, a password of at least 12 characters, and a non-blank `displayName`, the
`AuthController` shall create a new `User` with `role=USER` and a BCrypt-hashed password (the
plaintext password is never persisted), issue the same signed access-token cookie login issues
(AUTH-001-AC-10), and respond `201 Created` with body `{ id, email, displayName, role }`.

**Rationale**: `design-decisions.md`'s "Password hashing" and "Registration / login identifier"
sections, and its "Registration auto-login" section — auto-login after registration is standard
UX practice (avoids making a user who just typed their password immediately re-enter it), and
carries none of the usual session-fixation risk, since there's no pre-existing cookie/session for
an attacker to have fixated before this point and the JWT is freshly signed server-side at
issuance either way.

#### AUTH-001-AC-06 [AUTO]: Duplicate email rejected
**Statement**: If `POST /api/auth/register` is requested with an email that already matches an
existing user case-insensitively, then the `AuthController` shall respond `409 Conflict` and shall
not create a user.

**Rationale**: Enforces AUTH-001-AC-01's uniqueness constraint at the API layer with a meaningful
status code rather than surfacing a raw constraint-violation error. With `AUTH-001-AC-18`'s
normalisation in place, a same-or-different-case duplicate is caught directly by the up-front
lookup; the DB's functional unique index (and the `DataIntegrityViolationException` fallback that
catches it) remains purely as a safety net against a race between two concurrent registrations for
the same email.

#### AUTH-001-AC-07 [AUTO]: Password below minimum length rejected
**Statement**: If `POST /api/auth/register` is requested with a password shorter than 12
characters, then the `AuthController` shall respond `400 Bad Request` and shall not create a user.

**Rationale**: `design-decisions.md`'s password policy — length-only, no forced complexity.

#### AUTH-001-AC-08 [AUTO]: Malformed email or missing display name rejected
**Statement**: If `POST /api/auth/register` is requested with a malformed email address or a
blank/missing `displayName`, then the `AuthController` shall respond `400 Bad Request` and shall
not create a user.

**Rationale**: Validation of the two other required registration fields, independent of the
password-length check in AUTH-001-AC-07.

#### AUTH-001-AC-09 [AUTO]: Registration response never leaks credentials or the raw token
**Statement**: The `AuthController`'s registration response body shall never include the password,
the password hash, or the raw JWT string, under any outcome.

**Rationale**: Baseline credential-hygiene check — cheap to assert, expensive to regret. Extended
to the raw token once AUTH-001-AC-05 made registration issue one, mirroring AUTH-001-AC-13's same
check for login.

### Requirement 3: Login

**User story**: As a registered user, I want to log in with my email and password so that I
receive a session that authenticates my subsequent requests.

#### AUTH-001-AC-10 [AUTO]: Successful login issues an access-token cookie
**Statement**: When `POST /api/auth/login` is requested with an email and password matching an
existing user's stored hash, the `AuthController` shall issue a signed HS256 JWT access token
(15-minute expiry) as an httpOnly, Secure, `SameSite=Strict` cookie, and respond `200 OK` with body
`{ id, email, displayName, role }`.

**Rationale**: `design-decisions.md`'s "Access token" and "Transport & CSRF" sections.

#### AUTH-001-AC-11 [AUTO]: Failed login is rejected uniformly
**Statement**: If `POST /api/auth/login` is requested with an email that matches no user, or with
a password that doesn't match the stored hash for a matched user, then the `AuthController` shall
respond `401 Unauthorized` without indicating which of the two was wrong, and shall not set an
access-token cookie.

**Rationale**: Standard practice — a distinct "no such email" vs. "wrong password" response leaks
which emails are registered.

**Addendum (found during AUTH-001-AC-11 code review)**: this AC governs only genuine credential
mismatches — a syntactically well-formed email/password pair that simply doesn't match an
account. A structurally invalid request (blank or missing `email`/`password`) is a different
failure category, handled by `AUTH-001-AC-19` below. See that AC's rationale for why this split
doesn't weaken this AC's leak-prevention intent.

#### AUTH-001-AC-12 [AUTO]: Access-token claims are minimal
**Statement**: The access-token JWT issued by login shall contain no claims beyond the user's id
(`sub`) and role — specifically, it shall not contain the user's email or `displayName`.

**Rationale**: `design-decisions.md`'s "Access token" section — no PII in the token; a 15-minute
stale role claim after a demotion is an accepted tradeoff, but email/displayName have no such
justification for being there at all.

#### AUTH-001-AC-13 [AUTO]: Login response never leaks credentials or the raw token
**Statement**: The `AuthController`'s login response body shall never include the password hash or
the raw JWT string, under any outcome.

**Rationale**: The token is cookie-only by design (`design-decisions.md`) — the response body
exists only to give the frontend the user's displayable identity.

#### AUTH-001-AC-19 [AUTO]: Blank or missing login fields rejected as a malformed request
**Statement**: If `POST /api/auth/login` is requested with a blank or missing `email` or
`password` field, then the `AuthController` shall respond `400 Bad Request` and shall not set an
access-token cookie.

**Rationale**: Added during `AUTH-001-AC-11` code review — `LoginRequest`'s `@NotBlank`
constraints already produce this behaviour incidentally via `GlobalExceptionHandler`, but it had
never been a deliberate, documented, or tested decision. Decided to keep it as a `400`, matching
the same malformed-input treatment `AUTH-001-AC-08` already gives registration: a blank/missing
field carries no information about whether any account exists (it fails identically either way),
so it doesn't implicate `AUTH-001-AC-11`'s "don't leak which emails are registered" concern —
that AC governs credential *mismatches*, not structurally invalid requests.

### Requirement 4: Session rehydration (`GET /api/auth/me`)

**User story**: As a logged-in user whose browser holds an httpOnly cookie it can't read directly,
I want an endpoint that confirms who I am so the app can restore my session after a page refresh.

#### AUTH-001-AC-14 [AUTO]: Valid session returns the current user
**Statement**: When `GET /api/auth/me` is requested with a valid, unexpired access-token cookie,
the `AuthController` shall respond `200 OK` with body `{ id, email, displayName, role }` for the
user identified by that token.

**Rationale**: `design-decisions.md`'s "Display name" section — this endpoint exists specifically
because the frontend can never read an httpOnly cookie's contents itself.

**Addendum (added alongside AUTH-001-AC-20/AC-23)**: this AC's externally-observable behaviour
(`200` with the user body) is unchanged, but the mechanism behind it is not — `AuthController.
getCurrentUser` no longer performs its own `@CookieValue` extraction and `JwtService.getUserId`
call. `JwtAuthenticationFilter` (`AUTH-001-AC-20`) now populates `SecurityContextHolder` upstream,
before the controller method ever runs, and the controller simply reads the already-authenticated
principal from there.

#### AUTH-001-AC-15 [AUTO]: Missing or invalid session is rejected
**Statement**: If `GET /api/auth/me` is requested with a missing, malformed, expired, or
signature-invalid access-token cookie, then the `AuthController` shall respond
`401 Unauthorized`.

**Rationale**: Covers every way the cookie can fail to represent a currently-valid session, given
there's no refresh mechanism yet in this spec to fall back on.

**Addendum (added alongside AUTH-001-AC-21/AC-22)**: likewise, this `401` no longer comes from a
manual null-check inside `AuthController`. A missing, malformed, expired, or invalid-signature
cookie now simply leaves `SecurityContextHolder` unauthenticated (`AUTH-001-AC-21`/`AUTH-001-AC-22`),
and the `.authenticated()` rule plus `JsonAuthenticationEntryPoint` already configured in
`SecurityConfig` produce this `401` before `AuthController` is ever invoked.

### Requirement 5: Logout

**User story**: As a logged-in user, I want to log out so that my browser no longer carries a
usable access token.

#### AUTH-001-AC-16 [AUTO]: Logout clears the access-token cookie
**Statement**: When `POST /api/auth/logout` is requested, the `AuthController` shall clear the
access-token cookie (an immediately-expiring `Set-Cookie` matching the original cookie's name,
path, and attributes) and respond `200 OK`.

**Rationale**: `design-decisions.md`'s "Logout" section, scoped to what stage 1 can actually do —
no refresh-token table exists yet to revoke server-side, so a cookie clear is the entire mechanism
for now. (Stage 2 adds the server-side revocation step once refresh tokens exist.)

#### AUTH-001-AC-17 [AUTO]: Logout actually ends the session
**Statement**: When `GET /api/auth/me` is requested using the cookie that was present on the
client immediately after a `POST /api/auth/logout` response, the `AuthController` shall respond
`401 Unauthorized`.

**Rationale**: Verifies AUTH-001-AC-16's cookie-clear is effective, not just a `200` with no actual
effect — the one thing worth checking end-to-end given logout has no server-side state in this
spec.

### Requirement 6: Email normalisation (cross-cutting)

**User story**: As the system, I need every code path that reads or writes `email` to treat case
variants as the same identity, so that AUTH-001-AC-01's case-insensitive uniqueness guarantee is
actually honoured end-to-end, not just at the database constraint.

**Added**: this requirement wasn't part of the original spec — it was added after
AUTH-001-AC-10/AC-11 code review surfaced that `UserRepository.findByEmail` is a case-sensitive
lookup (`WHERE email = ?`) against a plain `VARCHAR` column, while AUTH-001-AC-01's
case-insensitivity is enforced only by a separate functional unique index (`lower(email)`). Without
this fix, a user registered as `Foo@x.com` who later logs in as `foo@x.com` would incorrectly get
AUTH-001-AC-11's "no such user" rejection.

#### AUTH-001-AC-18 [AUTO]: Email normalised to lowercase before any persistence or lookup
**Statement**: Before performing any persistence or lookup operation keyed on `email`, the
`AuthService` shall normalize the submitted email to lowercase.

**Rationale**: Normalising once at the `AuthService` boundary — rather than teaching every
repository query to be case-insensitive — makes every `findByEmail` call (both registration's
duplicate check and login's user lookup) a plain equality comparison that's correct by
construction. The database's functional unique index on `lower(email)` is retained, but demoted
from the primary case-insensitivity mechanism to a race-condition safety net (per AUTH-001-AC-06's
updated rationale above).

### Requirement 7: JWT authentication filter

**User story**: As the system, I need Spring Security's own authentication mechanism actually
populated from the access-token cookie — not bypassed by ad hoc controller-side parsing — so that
`authenticated()`-gated routes like `GET /api/auth/me` are reachable in the first place, and so
every future protected endpoint gets the same guarantee for free.

**Added**: this requirement wasn't part of the original spec — it surfaced while hand-coding
AUTH-001-AC-14/AC-15. `SecurityConfig` already gates every `/api/**` request behind
`.authenticated()`, except the two `permitAll` login/register POSTs, but nothing ever populated
`SecurityContextHolder` — `AuthController.getCurrentUser` did its own manual `@CookieValue`
extraction and called `JwtService.getUserId` directly, entirely bypassing Spring Security's
authentication mechanism. The practical symptom: every request to `/api/auth/me` was rejected
`401` by the security filter chain (via the existing `JsonAuthenticationEntryPoint`) before the
controller was ever invoked.

#### AUTH-001-AC-20 [AUTO]: Valid cookie populates the SecurityContext
**Statement**: When a request carries a valid, unexpired, correctly-signed `access_token` cookie,
the `JwtAuthenticationFilter` shall populate `SecurityContextHolder` with an authenticated
`Authentication` built from that token's user id and role, before the filter chain's authorisation
check runs.

**Rationale**: This is the mechanism that actually makes `AUTH-001-AC-14` possible —
`SecurityConfig`'s `.authenticated()` rule has nothing to authorise against otherwise. Extraction
goes through the existing `JwtService` (`AUTH-001-AC-10`'s issuance counterpart), so token
parsing/validation logic isn't duplicated between issuance and verification.

#### AUTH-001-AC-21 [AUTO]: Missing cookie leaves the request unauthenticated
**Statement**: When a request carries no `access_token` cookie, the `JwtAuthenticationFilter`
shall continue the filter chain without setting an `Authentication` in `SecurityContextHolder`.

**Rationale**: The two `permitAll` routes (`POST /api/auth/login`, `POST /api/auth/register`) must
keep working with no cookie present at all — a cookie-less request is the normal case for those
two, not a failure the filter itself should reject. Whether the resulting lack of authentication
then matters is left entirely to `SecurityConfig`'s existing per-route rules downstream.

#### AUTH-001-AC-22 [AUTO]: Malformed, expired, or invalid-signature cookie doesn't propagate as a server error
**Statement**: If a request's `access_token` cookie is malformed, expired, or has an invalid
signature — i.e. `JwtService` throws `JwtParserException` while the `JwtAuthenticationFilter`
attempts to read it — then the `JwtAuthenticationFilter` shall catch that exception, leave
`SecurityContextHolder` unauthenticated, and continue the filter chain.

**Rationale**: Without this, a bad cookie would surface as an unhandled `500` from inside the
filter chain, before `GlobalExceptionHandler` (which only wraps controller-layer exceptions) ever
gets a chance to see it. Leaving the context unauthenticated instead lets `SecurityConfig`'s
existing `.authenticated()` rule and `JsonAuthenticationEntryPoint` produce the already-specified
`401` (`AUTH-001-AC-15`), rather than inventing a second, filter-level error path.

#### AUTH-001-AC-23 [AUTO]: Filter is registered in the security filter chain
**Statement**: The `SecurityConfig` filter chain shall register `JwtAuthenticationFilter` to run
before `AuthorizationFilter`.

**Rationale**: The filter must run early enough that its `SecurityContextHolder` population is in
place before Spring Security's own authorisation decision (`.authenticated()`) runs — `AuthorizationFilter`
is what actually makes that decision, so that's the real ordering constraint. (`UsernamePasswordAuthenticationFilter`
isn't a usable reference point here: it's only ever constructed when something calls `.formLogin()`,
which this app never does — it authenticates via a custom JSON login, not Spring Security's
form-login mechanism — so that filter never exists in this app's chain to be "before" at all.)

### Requirement 8: Supporting collaborators for session rehydration

**User story**: As the system, I need `JwtService.getClaim` and `AuthService.getUserDetailsById` —
the two collaborators `AuthController.getCurrentUser` and `JwtAuthenticationFilter` depend on — to
have their own directly-tested contracts, so that `AUTH-001-AC-14`/`AUTH-001-AC-15`'s externally
observable `200`/`401` behaviour is backed by verified unit-level behaviour at each collaborator,
not just the end-to-end controller test.

**Added**: this requirement wasn't part of the original spec — a pre-commit review of the
AUTH-001-AC-14/AC-15 implementation found six existing, already-passing tests (two each in
`JwtServiceSpec`, `AuthServiceSpec`, `AuthServiceDataJpaSpec`) mislabeled `AUTH-001-AC-14`, even
though that AC is specifically about `GET /api/auth/me`'s controller-level response and is already
correctly covered by `AuthControllerSpec`. The tests themselves were sound and worth keeping — they
needed their own ACs to point to, not AC-14's.

#### AUTH-001-AC-24 [AUTO]: `JwtService.getClaim` validates the subject and role claims
**Statement**: When `JwtService.getClaim` is given a JWT whose `sub` claim is a valid UUID and whose
`role` claim is non-blank, it shall return a `JwtClaim` with that subject and role; if either claim
is missing, blank, or the subject is not a valid UUID, it shall throw `JwtParserException`.

**Rationale**: This is the contract `JwtAuthenticationFilter` depends on for `AUTH-001-AC-20`
(successful population) and `AUTH-001-AC-22` (catching `JwtParserException` for anything else) —
pinning it down directly means a future change to claim validation gets caught here, not only
indirectly through the filter's own tests.

#### AUTH-001-AC-25 [AUTO]: `AuthService.getUserDetailsById` returns the user or fails closed
**Statement**: When `AuthService.getUserDetailsById` is given the id of an existing user, it shall
return a `UserResponse` for that user; if given an id matching no user, it shall throw
`UserException`, which `GlobalExceptionHandler` maps to `401 Unauthorized`.

**Rationale**: This is the lookup `AuthController.getCurrentUser` performs once
`JwtAuthenticationFilter` has already authenticated the request (`AUTH-001-AC-14`) — the "no such
user" branch covers the edge case of a still-valid token for a user who no longer exists (e.g.
deleted between token issuance and use), which `AUTH-001-AC-14`/`AUTH-001-AC-15`'s black-box
controller tests don't exercise directly.

## Cross-references

| Contract                                                                                                                   | Defined here                                                                                            | Used by                                                                                                                                                                                 |
|----------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `User` entity (`email`, password hash, `displayName`, `role`)                                                              | This spec                                                                                               | `backend_spec_002_refresh_tokens` (future, stage 2), `backend_spec_003_admin_account_management` (future, stage 3), `frontend_spec_001_basic_login.md`                                  |
| `POST /api/auth/register`                                                                                                  | This spec                                                                                               | `frontend_spec_001_basic_login.md`                                                                                                                                                      |
| `POST /api/auth/login`                                                                                                     | This spec                                                                                               | `frontend_spec_001_basic_login.md`                                                                                                                                                      |
| `GET /api/auth/me`                                                                                                         | This spec                                                                                               | `frontend_spec_001_basic_login.md` — session rehydration on app load                                                                                                                    |
| `POST /api/auth/logout`                                                                                                    | This spec                                                                                               | `frontend_spec_001_basic_login.md`                                                                                                                                                      |
| Access-token JWT shape (`sub` = user id, `role` claim, HS256, 15 min)                                                      | `.claude/01-authentication/design-decisions.md` ("Access token")                                        | This spec (issuance/validation); `backend_spec_002_refresh_tokens` (future reissue-on-refresh)                                                                                          |
| Password policy (BCrypt, min length 12, no complexity rule)                                                                | `.claude/01-authentication/design-decisions.md` ("Password hashing", "Registration / login identifier") | This spec                                                                                                                                                                               |
| Role column, `USER`/`ADMIN` enum                                                                                           | `.claude/01-authentication/design-decisions.md` ("Access token", "Admin bootstrap")                     | This spec (column only); `backend_spec_003_admin_account_management` (future — first spec to act on the distinction)                                                                    |
| Email verification (explicitly not built)                                                                                  | `.claude/01-authentication/spec_candidates.md`                                                          | N/A — confirms no verification columns/endpoints belong in this spec                                                                                                                    |
| Email normalization (lowercase at the `AuthService` boundary)                                                              | This spec (`AUTH-001-AC-18`)                                                                            | `AUTH-001-AC-01` (uniqueness), `AUTH-001-AC-06` (duplicate check), `AUTH-001-AC-10`/`AUTH-001-AC-11` (login lookup) — all within this spec                                              |
| `JwtAuthenticationFilter` (populates `SecurityContextHolder` from the `access_token` cookie via the existing `JwtService`) | This spec (`AUTH-001-AC-20` through `AUTH-001-AC-23`)                                                   | `AUTH-001-AC-14`/`AUTH-001-AC-15` (makes `GET /api/auth/me`'s `.authenticated()` gate actually work); every future `/api/**` endpoint behind `SecurityConfig`'s `.authenticated()` rule |
| `JwtService.getClaim` / `AuthService.getUserDetailsById` contracts                                                         | This spec (`AUTH-001-AC-24`, `AUTH-001-AC-25`)                                                          | `AUTH-001-AC-14`/`AUTH-001-AC-15` (the collaborators `AuthController.getCurrentUser` and `JwtAuthenticationFilter` depend on)                                                           |

## Acceptance Criteria Summary

- [x] AUTH-001-AC-01 [AUTO]: Case-insensitive unique email constraint on `User`
- [x] AUTH-001-AC-02 [AUTO]: `User.role` defaults to `USER`
- [x] AUTH-001-AC-03 [AUTO]: `User.displayName` is required
- [x] AUTH-001-AC-04 [AUTO]: `users` table schema matches entity mapping at startup
- [x] AUTH-001-AC-05 [AUTO]: Successful registration creates a `USER` with a hashed password, sets the access-token cookie, returns 201
- [x] AUTH-001-AC-06 [AUTO]: Duplicate (case-insensitive) email registration returns 409, no user created
- [x] AUTH-001-AC-07 [AUTO]: Password under 12 characters returns 400, no user created
- [x] AUTH-001-AC-08 [AUTO]: Malformed email or blank display name returns 400, no user created
- [x] AUTH-001-AC-09 [AUTO]: Registration response never includes password/hash/raw JWT
- [x] AUTH-001-AC-10 [AUTO]: Successful login sets the access-token cookie, returns 200 with user body
- [x] AUTH-001-AC-11 [AUTO]: Failed login (bad email or password) returns 401 uniformly, no cookie set
- [x] AUTH-001-AC-12 [AUTO]: Access-token claims are limited to user id and role
- [x] AUTH-001-AC-13 [AUTO]: Login response never includes password hash or raw JWT
- [x] AUTH-001-AC-14 [AUTO]: `GET /api/auth/me` with a valid cookie returns the current user
- [x] AUTH-001-AC-15 [AUTO]: `GET /api/auth/me` with a missing/invalid/expired cookie returns 401
- [ ] AUTH-001-AC-16 [AUTO]: Logout clears the access-token cookie, returns 200
- [ ] AUTH-001-AC-17 [AUTO]: A session is actually unusable immediately after logout
- [x] AUTH-001-AC-18 [AUTO]: Email is normalised to lowercase before any persistence/lookup operation
- [x] AUTH-001-AC-19 [AUTO]: Blank or missing login fields return 400, no cookie set
- [x] AUTH-001-AC-20 [AUTO]: Valid access-token cookie populates SecurityContextHolder before authorisation runs
- [x] AUTH-001-AC-21 [AUTO]: Missing cookie continues the chain unauthenticated; permitAll routes unaffected
- [x] AUTH-001-AC-22 [AUTO]: Malformed/expired/invalid-signature cookie is caught, not propagated; SecurityContext stays unauthenticated
- [x] AUTH-001-AC-23 [AUTO]: JwtAuthenticationFilter is registered in SecurityConfig's chain before AuthorizationFilter
- [x] AUTH-001-AC-24 [AUTO]: JwtService.getClaim returns claims for a valid token, throws JwtParserException otherwise
- [x] AUTH-001-AC-25 [AUTO]: AuthService.getUserDetailsById returns the user when found, throws UserException otherwise

## Test case sketches

Per the Working Agreement, these are scenario + block-structure sketches only — `backend-dev`
turns each into a label-only Spock skeleton; the human fills in real `given`/`when`/`then` content.

```groovy
def "AUTH-001-AC-01: rejects a second user whose email differs only by case"() {
    given: "a persisted user with a given email"

    when: "a second user is persisted with the same email in different case"

    then: "persistence fails on the unique constraint"
}
```

```groovy
def "AUTH-001-AC-02: defaults role to USER when not set"() {
    given: "a new User built without an explicit role"

    when: "the user is persisted"

    then: "the persisted user's role is USER"
}
```

```groovy
def "AUTH-001-AC-03: rejects a blank display name"() {
    given: "a new User with a null or blank displayName"

    when: "the user is persisted"

    then: "persistence fails validation"
}
```

```groovy
def "AUTH-001-AC-04: application context loads with a schema-valid users table"() {
    given: "the Flyway migration for the users table has run"

    when: "the Spring application context loads"

    then: "startup succeeds with no Hibernate schema-validation error"
}
```

```groovy
def "AUTH-001-AC-05: registers a new user with a valid request and logs them in"() {
    given: "a valid registration request (unused email, 12+ char password, non-blank displayName)"

    when: "POST /api/auth/register is requested"

    then: "the response is 201 Created with the new user's id, email, displayName, and role"

    and: "the persisted password is a BCrypt hash, not the plaintext value"

    and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
}
```

```groovy
def "AUTH-001-AC-06: rejects registration with a duplicate email (case-insensitive)"() {
    given: "an existing user with a given email"

    when: "POST /api/auth/register is requested with the same email in a different case"

    then: "the response is 409 Conflict"

    and: "no new user is created"
}
```

```groovy
def "AUTH-001-AC-07: rejects registration with a too-short password"() {
    given: "a registration request with a password under 12 characters"

    when: "POST /api/auth/register is requested"

    then: "the response is 400 Bad Request"

    and: "no new user is created"
}
```

```groovy
def "AUTH-001-AC-08: rejects registration with a malformed email or blank display name"() {
    given: "a registration request with either a malformed email or a blank displayName"

    when: "POST /api/auth/register is requested"

    then: "the response is 400 Bad Request"

    and: "no new user is created"
}
```

```groovy
def "AUTH-001-AC-09: registration response never includes credentials or the raw token"() {
    given: "any registration request, successful or rejected"

    when: "POST /api/auth/register is requested"

    then: "the response body contains neither a password field, a password-hash field, nor the raw JWT string"
}
```

```groovy
def "AUTH-001-AC-10: successful login issues an access-token cookie"() {
    given: "an existing user with a known email and password"

    when: "POST /api/auth/login is requested with matching credentials"

    then: "the response is 200 OK with the user's id, email, displayName, and role"

    and: "the response sets an httpOnly, Secure, SameSite=Strict access-token cookie expiring in 15 minutes"
}
```

```groovy
def "AUTH-001-AC-11: rejects login with a non-existent email or a wrong password"() {
    given: "either no user with the given email, or a user with a non-matching password"

    when: "POST /api/auth/login is requested"

    then: "the response is 401 Unauthorized with a generic message"

    and: "no access-token cookie is set"
}
```

```groovy
def "AUTH-001-AC-12: access token claims are limited to id and role"() {
    given: "an existing user"

    when: "POST /api/auth/login is requested with matching credentials"

    then: "the issued JWT's claims contain only the user's id and role"
}
```

```groovy
def "AUTH-001-AC-13: login response never includes credentials or the raw token"() {
    given: "a successful login"

    when: "POST /api/auth/login is requested"

    then: "the response body contains neither the password hash nor the raw JWT string"
}
```

```groovy
def "AUTH-001-AC-14: returns the current user for a valid session"() {
    given: "a valid, unexpired access-token cookie for an existing user"

    when: "GET /api/auth/me is requested with that cookie"

    then: "the response is 200 OK with the user's id, email, displayName, and role"
}
```

```groovy
def "AUTH-001-AC-15: rejects a missing, invalid, or expired session"() {
    given: "a request with either no cookie, a malformed cookie, or an expired/invalid-signature token"

    when: "GET /api/auth/me is requested"

    then: "the response is 401 Unauthorized"
}
```

```groovy
def "AUTH-001-AC-16: logout clears the access-token cookie"() {
    given: "a logged-in user with a valid access-token cookie"

    when: "POST /api/auth/logout is requested"

    then: "the response is 200 OK"

    and: "the response clears the access-token cookie"
}
```

```groovy
def "AUTH-001-AC-17: the session is unusable immediately after logout"() {
    given: "a logged-in user who has just logged out"

    when: "GET /api/auth/me is requested using the cookie returned by the logout response"

    then: "the response is 401 Unauthorized"
}
```

```groovy
def "AUTH-001-AC-18: registration persists a lowercase email regardless of input casing"() {
    given: "a registration request with a mixed-case email"

    when: "POST /api/auth/register is requested"

    then: "the persisted user's email is the lowercase form"
}
```

```groovy
def "AUTH-001-AC-18: login succeeds with an email differing only by case from registration"() {
    given: "a user registered with a mixed-case email"

    when: "POST /api/auth/login is requested with the same email in a different case"

    then: "the response is 200 OK, matching the user by normalized email"
}
```

```groovy
def "AUTH-001-AC-19: rejects login with a blank or missing email or password"() {
    given: "a login request with a blank or missing email or password field"

    when: "POST /api/auth/login is requested"

    then: "the response is 400 Bad Request"

    and: "no access-token cookie is set"
}
```

```groovy
def "AUTH-001-AC-20: populates the SecurityContext for a valid access-token cookie"() {
    given: "a request carrying a valid, unexpired, correctly-signed access-token cookie"

    when: "the request passes through JwtAuthenticationFilter"

    then: "SecurityContextHolder holds an authenticated Authentication for the token's user id and role"
}
```

```groovy
def "AUTH-001-AC-21: leaves the request unauthenticated when no cookie is present"() {
    given: "a request with no access-token cookie"

    when: "the request passes through JwtAuthenticationFilter"

    then: "SecurityContextHolder has no Authentication set"

    and: "the filter chain continues to the next filter"
}
```

```groovy
def "AUTH-001-AC-22: swallows a malformed, expired, or invalid-signature cookie without a 500"() {
    given: "a request whose access-token cookie causes JwtService to throw JwtParserException"

    when: "the request passes through JwtAuthenticationFilter"

    then: "no exception propagates out of the filter"

    and: "SecurityContextHolder has no Authentication set"

    and: "the filter chain continues to the next filter"
}
```

```groovy
def "AUTH-001-AC-23: SecurityConfig registers JwtAuthenticationFilter ahead of AuthorizationFilter"() {
    given: "the configured SecurityFilterChain"

    when: "the filter chain's filter order is inspected"

    then: "JwtAuthenticationFilter runs before AuthorizationFilter"
}
```

```groovy
def "AUTH-001-AC-24: returns claims for a valid token, throws for an invalid one"() {
    given: "a JWT whose sub/role claims are either both valid, or one is missing/blank/malformed"

    when: "JwtService.getClaim is called with that JWT"

    then: "a valid token returns a JwtClaim with the expected subject and role"

    and: "a token with a missing, blank, or malformed subject or role throws JwtParserException"
}
```

```groovy
def "AUTH-001-AC-25: returns the user when found, throws when not"() {
    given: "either an existing user's id, or an id matching no user"

    when: "AuthService.getUserDetailsById is called with that id"

    then: "an existing user's id returns a UserResponse for that user"

    and: "an id matching no user throws UserException"
}
```

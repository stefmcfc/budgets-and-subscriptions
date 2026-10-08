# Basic Login (Backend)

**Status**: Not started
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
status code rather than surfacing a raw constraint-violation error.

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

### Requirement 4: Session rehydration (`GET /api/auth/me`)

**User story**: As a logged-in user whose browser holds an httpOnly cookie it can't read directly,
I want an endpoint that confirms who I am so the app can restore my session after a page refresh.

#### AUTH-001-AC-14 [AUTO]: Valid session returns the current user
**Statement**: When `GET /api/auth/me` is requested with a valid, unexpired access-token cookie,
the `AuthController` shall respond `200 OK` with body `{ id, email, displayName, role }` for the
user identified by that token.

**Rationale**: `design-decisions.md`'s "Display name" section — this endpoint exists specifically
because the frontend can never read an httpOnly cookie's contents itself.

#### AUTH-001-AC-15 [AUTO]: Missing or invalid session is rejected
**Statement**: If `GET /api/auth/me` is requested with a missing, malformed, expired, or
signature-invalid access-token cookie, then the `AuthController` shall respond
`401 Unauthorized`.

**Rationale**: Covers every way the cookie can fail to represent a currently-valid session, given
there's no refresh mechanism yet in this spec to fall back on.

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

## Cross-references

| Contract | Defined here | Used by |
|---|---|---|
| `User` entity (`email`, password hash, `displayName`, `role`) | This spec | `backend_spec_002_refresh_tokens` (future, stage 2), `backend_spec_003_admin_account_management` (future, stage 3), `frontend_spec_001_basic_login.md` |
| `POST /api/auth/register` | This spec | `frontend_spec_001_basic_login.md` |
| `POST /api/auth/login` | This spec | `frontend_spec_001_basic_login.md` |
| `GET /api/auth/me` | This spec | `frontend_spec_001_basic_login.md` — session rehydration on app load |
| `POST /api/auth/logout` | This spec | `frontend_spec_001_basic_login.md` |
| Access-token JWT shape (`sub` = user id, `role` claim, HS256, 15 min) | `.claude/01-authentication/design-decisions.md` ("Access token") | This spec (issuance/validation); `backend_spec_002_refresh_tokens` (future reissue-on-refresh) |
| Password policy (BCrypt, min length 12, no complexity rule) | `.claude/01-authentication/design-decisions.md` ("Password hashing", "Registration / login identifier") | This spec |
| Role column, `USER`/`ADMIN` enum | `.claude/01-authentication/design-decisions.md` ("Access token", "Admin bootstrap") | This spec (column only); `backend_spec_003_admin_account_management` (future — first spec to act on the distinction) |
| Email verification (explicitly not built) | `.claude/01-authentication/spec_candidates.md` | N/A — confirms no verification columns/endpoints belong in this spec |

## Acceptance Criteria Summary

- [ ] AUTH-001-AC-01 [AUTO]: Case-insensitive unique email constraint on `User`
- [ ] AUTH-001-AC-02 [AUTO]: `User.role` defaults to `USER`
- [ ] AUTH-001-AC-03 [AUTO]: `User.displayName` is required
- [ ] AUTH-001-AC-04 [AUTO]: `users` table schema matches entity mapping at startup
- [ ] AUTH-001-AC-05 [AUTO]: Successful registration creates a `USER` with a hashed password, sets the access-token cookie, returns 201
- [ ] AUTH-001-AC-06 [AUTO]: Duplicate (case-insensitive) email registration returns 409, no user created
- [ ] AUTH-001-AC-07 [AUTO]: Password under 12 characters returns 400, no user created
- [ ] AUTH-001-AC-08 [AUTO]: Malformed email or blank display name returns 400, no user created
- [ ] AUTH-001-AC-09 [AUTO]: Registration response never includes password/hash/raw JWT
- [ ] AUTH-001-AC-10 [AUTO]: Successful login sets the access-token cookie, returns 200 with user body
- [ ] AUTH-001-AC-11 [AUTO]: Failed login (bad email or password) returns 401 uniformly, no cookie set
- [ ] AUTH-001-AC-12 [AUTO]: Access-token claims are limited to user id and role
- [ ] AUTH-001-AC-13 [AUTO]: Login response never includes password hash or raw JWT
- [ ] AUTH-001-AC-14 [AUTO]: `GET /api/auth/me` with a valid cookie returns the current user
- [ ] AUTH-001-AC-15 [AUTO]: `GET /api/auth/me` with a missing/invalid/expired cookie returns 401
- [ ] AUTH-001-AC-16 [AUTO]: Logout clears the access-token cookie, returns 200
- [ ] AUTH-001-AC-17 [AUTO]: A session is actually unusable immediately after logout

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

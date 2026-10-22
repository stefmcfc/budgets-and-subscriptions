# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

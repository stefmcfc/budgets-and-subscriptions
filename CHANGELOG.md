# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

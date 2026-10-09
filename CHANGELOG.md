# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `User` JPA entity and first Flyway migration (`V001__create_users_table.sql`), backing the
  in-progress authentication feature: case-insensitive unique email, required display name,
  role defaulting to `USER`.

# Budgets and Subscriptions

## What it does

A personal finance tracker: accounts and transactions across multiple account types, spending
insights (categories, recurring/subscription detection, big-purchase flags), multi-person running
balances (money owed by/to other people), and reminders for date-driven things like bank-switching
offers and subscription renewals.

Full background and the question-by-question decision history: `.claude/00-initial-setup/`.

## Who it's for

Personal use — single real user, but built multi-user with a `USER`/`ADMIN` role split from the
start, specifically so the owner gets hands-on auth/admin-tooling experience (see `tech.md`'s Auth
section) — not because a second real user is expected. Don't build anything beyond that
(self-registration throttling, billing, per-tenant anything) ahead of a concrete need.

## Primary goal

**Learning Spring Boot properly by hand-coding the core layers** — the app itself is secondary to
that. See root `CLAUDE.md`'s Working Agreement: most of the backend (entities, repositories,
services, controllers, security, real tests) is deliberately hand-coded by the human, not written
by Claude. This shapes everything else in `.claude/` — specs exist so the human has a clear,
reviewed target to implement against, not so Claude can implement them directly.

## Key features

1. **Accounts and transactions** — money in/out across multiple account types (bank current/
   savings, Cash ISA, investment/pension, credit cards). User-defined categories (presets
   available), a transaction can have more than one.
2. **Transaction classification engine** — one shared pattern-matching mechanism (merchant,
   amount, frequency) behind category suggestion, recurring/subscription detection, and
   big-purchase flagging. Manual tagging always available, can override an automatic match.
3. **Spending insights** — monthly totals and breakdowns by category/recurring-merchant, income
   vs. expenditure, flagged big one-off purchases.
4. **Multi-person running balances** — track money owed by/to other people (e.g. brother, friend,
   parent) for purchases made on their behalf, including a starting balance and cash transactions
   not tied to any bank account.
5. **Reminders** — general date-triggered notifications (bank-switching bonus-rate expiry,
   subscription/insurance renewal review).
6. **Auth & admin** — registration, login (JWT via httpOnly cookie + refresh tokens), and an admin
   role with account-management pages.

## Build order

1. **Authentication** — simplest secure version first (login/registration → DB storage →
   front end), then refresh tokens and the admin role once the basic flow is understood.
2. Accounts and transactions
3. Categories, the transaction classification engine, and spending summaries
4. Multi-person running balances
5. Reminders

See root `CLAUDE.md` for current status against this order.

## Goals

- The human genuinely practices hand-coding the backend — entities, repositories, services,
  controllers, security, and real Spock tests — with Claude scaffolding and reviewing, not
  authoring.
- A real, usable personal finance tracker as the byproduct, not the point.
- Deliberate, hands-on exposure to things worth learning properly: JWT auth with refresh tokens,
  an admin role, Flyway migrations, red/green TDD.

## Non-goals

- **Bill-splitting** (dividing a single transaction's cost between people, e.g. 60/40) — a
  deliberately deferred future idea, not being built now. Don't build it ahead of a concrete
  decision to pick it up.
- Multi-tenant features beyond the one admin/one real-user split already decided — no
  self-registration throttling, billing, or per-tenant infrastructure.
- Bank integration / Open Banking, CSV import — manual entry only for now; may be reconsidered
  later.
- Mobile app — web-first.

## Known constraints

- Single currency (GBP), `BigDecimal` for amounts. Foreign-currency transactions may be flagged
  informationally, but conversion/multi-currency accounting is out of scope.
- Local-only hosting for now — no cloud deployment.
- Financial and personal data (account balances, who owes whom) is treated as sensitive — never
  logged in plaintext beyond what's needed, never sent anywhere beyond this app's own database.

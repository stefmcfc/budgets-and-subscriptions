# Transactions (Backend)

**Status**: Not started
**Priority**: P1 — second and final spec of build order step 2 ("Accounts and transactions");
completes that step once implemented.
**Depends on**: `backend_spec_002_accounts.md` (the `Account` entity transactions attach to and
mutate the balance of, the owner-scoping pattern via `@AuthenticationPrincipal String userId`, the
`GlobalExceptionHandler`/custom-exception pattern, and the malformed/absent-id-handling family —
`BACKEND-002-AC-24a` through `AC-24f` — this spec reuses rather than reinvents)
**Area**: Backend

## Overview

This delivers the `Transaction` entity and its full CRUD surface, every endpoint scoped to an
`Account` the authenticated user owns: `amount` (a signed `BigDecimal` — positive for money in,
negative for money out), `date` (must fall on or after the owning account's `openedOn`), free-text
`description`, and an `Account` foreign key. A `Transaction` carries no owner column of its own —
ownership is transitive through its `Account`, so every authorisation check in this spec reduces to
"does the current user own this transaction's account," never a second, independently-maintained
owner reference. The one genuinely hard part of this spec is keeping `Account.balance` correct as
transactions are created, edited, and deleted against it, including under concurrent writes to the
same account — `balance` is a stored, transactionally-maintained column (not computed at read
time), and the locking strategy that keeps it correct under a race is deliberately left as a choice
for whoever hand-codes `TransactionService`, not a decision this spec makes for them (see
Requirement 7). This is deliberately `Transaction` alone — no category, recurring-flag, or other
classification column at all (that's build order step 3's job), and none of the deferred account
shapes (credit cards, investment/pension accounts, foreign-currency flagging, sensitive account
identifiers, archiving) that `backend_spec_002_accounts.md` already ruled out of scope for this
stage. Full rationale for every field and scope boundary lives in
`.claude/02-accounts-and-transactions/design-decisions.md`; deferred concepts are tracked in that
stage's `spec_candidates.md`, not re-litigated here.

## Requirements

### Requirement 1: `Transaction` entity and schema

**User story**: As the system, I need a durable, account-scoped transaction record so that later
classification, spending-insight, and balance features have something correct to operate on.

#### BACKEND-003-AC-01 [AUTO]: Required amount
**Statement**: The `Transaction` entity shall reject persistence of a transaction with a null
`amount`.

**Rationale**: `design-decisions.md`'s "Transaction fields" — `amount` is required and signed
(positive = money in, negative = money out), chosen over a separate `type` enum + unsigned amount
so balance arithmetic is a plain `balance + amount` at every step. This AC's test coverage includes
confirming both a positive and a negative amount persist successfully, not just that a null one is
rejected.

#### BACKEND-003-AC-02 [AUTO]: Required date
**Statement**: The `Transaction` entity shall reject persistence of a transaction with a null
`date`.

**Rationale**: `date` is required. The further "on or after the owning account's `openedOn`" rule
is a cross-entity check against a different entity's field, so it is enforced at the API/service
layer (`BACKEND-003-AC-10` for create, `BACKEND-003-AC-22` for update), not as a plain entity-level
constraint here.

#### BACKEND-003-AC-03 [AUTO]: Required description
**Statement**: The `Transaction` entity shall reject persistence of a transaction with a null or
blank `description`.

**Rationale**: `design-decisions.md`'s "Transaction fields" — free text, required, no separate
structured merchant field in this stage.

#### BACKEND-003-AC-04 [AUTO]: Account required
**Statement**: If a `Transaction` is persisted with no owning `Account` set, then persistence shall
fail.

**Rationale**: Every transaction belongs to exactly one account; there is no unattached-transaction
concept. This FK is also the entirety of a transaction's authorisation surface — see the Overview.

#### BACKEND-003-AC-05 [AUTO]: Schema matches entity mapping
**Statement**: When the Spring application context loads, the `transactions` table created by its
Flyway migration shall match the `Transaction` entity's JPA mapping, such that startup succeeds
under `ddl-auto: validate`.

**Rationale**: Same enforcement mechanism as `BACKEND-002-AC-06`.

### Requirement 2: Create a transaction

**User story**: As a user, I want to record a new transaction against one of my accounts so that
its balance reflects what actually happened.

#### BACKEND-003-AC-06 [AUTO]: Valid creation returns the new transaction and updates the balance
**Statement**: When `POST /api/accounts/{accountId}/transactions` is requested by an authenticated
user for an `accountId` owned by that user, with a valid `amount`, `date`, and `description`, the
`TransactionController` shall create a new `Transaction` associated with that account, add the
transaction's `amount` to the account's `balance`, and respond `201 Created` with the created
transaction.

**Rationale**: The core create flow. Balance arithmetic is the plain `balance + amount` design
decided in `design-decisions.md`'s "Running balance"; keeping that mutation correct under
concurrent writes is Requirement 7's separate, dedicated AC, not restated here.

#### BACKEND-003-AC-07 [AUTO]: Not-found and not-owned account are indistinguishable
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested for an `accountId`
that does not exist, or that belongs to a different user, then the `TransactionController` shall
respond `404 Not Found` in both cases, and shall not create a transaction.

**Rationale**: Mirrors `BACKEND-002-AC-16`'s uniform-failure principle, applied to the account path
segment every transaction endpoint in this spec is scoped under.

#### BACKEND-003-AC-08 [AUTO]: Missing or invalid amount rejected
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested with a missing
`amount`, or an `amount` value that is not a valid decimal number, then the `TransactionController`
shall respond `400 Bad Request` and shall not create a transaction.

**Rationale**: Combines a missing field and a malformed value in one AC, the same combining pattern
`BACKEND-002-AC-10` used for `type`. The malformed-value case fails Jackson deserialization before
validation runs, surfacing via the existing `HttpMessageNotReadableException` → `400` handler — no
new code, only new test coverage.

#### BACKEND-003-AC-09 [AUTO]: Missing or blank description rejected
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested with a missing or
blank `description`, then the `TransactionController` shall respond `400 Bad Request` and shall not
create a transaction.

#### BACKEND-003-AC-10 [AUTO]: Missing or too-early date rejected
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested with a missing `date`,
or a `date` earlier than the owning account's `openedOn`, then the `TransactionController` shall
respond `400 Bad Request` and shall not create a transaction.

**Rationale**: `design-decisions.md`'s "Transaction fields" — a transaction dated before the
account's own opening-balance date has no coherent starting point to apply against. This is a
cross-entity check the `TransactionService` performs against the already-resolved `Account`, not a
plain bean-validation constraint on the request DTO alone.

#### BACKEND-003-AC-11a [AUTO]: A supplied-but-invalid accountId is a 400, not a 404 or 500 (create)
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested with an `accountId`
path segment that is present but not a valid UUID, then the `TransactionController` shall respond
`400 Bad Request` and shall not create a transaction.

**Rationale**: Same mechanism as `BACKEND-002-AC-24a` — the `accountId` path variable fails UUID
conversion before the controller method runs, handled by the existing
`MethodArgumentTypeMismatchException` → `400` mapping from `backend_spec_002_accounts.md`. No new
code: only new test coverage for this endpoint shape. `BACKEND-003-AC-11` is a single family
(reused, not renumbered, across every endpoint in this spec that has an `accountId` path segment —
`AC-11a`/`b` here for create, continuing through `AC-11j` by Requirement 6), mirroring
`BACKEND-002-AC-24`'s own reuse across `GET`/`PATCH`/`DELETE`.

#### BACKEND-003-AC-11b [AUTO]: An absent accountId is a 404, not a 400 or 500 (create)
**Statement**: If `POST /api/accounts/{accountId}/transactions` is requested with an `accountId`
path segment that is empty or blank, then the `TransactionController` shall respond `404 Not Found`
and shall not create a transaction.

**Rationale**: Same mechanism as `BACKEND-002-AC-24b`.

### Requirement 3: List transactions for an account

**User story**: As a user, I want to see every transaction recorded against one of my accounts.

#### BACKEND-003-AC-12 [AUTO]: Lists every transaction for an owned account
**Statement**: When `GET /api/accounts/{accountId}/transactions` is requested for an `accountId`
owned by the authenticated user, the `TransactionController` shall respond `200 OK` with every
`Transaction` belonging to that account.

#### BACKEND-003-AC-13 [AUTO]: Empty list when the account has no transactions
**Statement**: When `GET /api/accounts/{accountId}/transactions` is requested for an `accountId`
owned by the authenticated user that has no transactions, the `TransactionController` shall respond
`200 OK` with an empty list.

#### BACKEND-003-AC-14 [AUTO]: Not-found and not-owned account are indistinguishable
**Statement**: If `GET /api/accounts/{accountId}/transactions` is requested for an `accountId` that
does not exist, or that belongs to a different user, then the `TransactionController` shall respond
`404 Not Found` in both cases.

**Rationale**: Same uniform-failure principle as `BACKEND-003-AC-07`, applied to the list endpoint.

#### BACKEND-003-AC-11c [AUTO]: A supplied-but-invalid accountId is a 400, not a 404 or 500 (list)
**Statement**: If `GET /api/accounts/{accountId}/transactions` is requested with an `accountId`
path segment that is present but not a valid UUID, then the `TransactionController` shall respond
`400 Bad Request`.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family — same mechanism as `AC-11a`, applied
to this verb.

#### BACKEND-003-AC-11d [AUTO]: An absent accountId is a 404, not a 400 or 500 (list)
**Statement**: If `GET /api/accounts/{accountId}/transactions` is requested with an `accountId`
path segment that is empty or blank, then the `TransactionController` shall respond `404 Not
Found`.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family — same mechanism as `AC-11b`.

### Requirement 4: View a single transaction

**User story**: As a user, I want to view one transaction's full detail.

#### BACKEND-003-AC-15 [AUTO]: Returns an owned transaction
**Statement**: When `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested for
an `accountId` and `transactionId` both owned by the authenticated user, the
`TransactionController` shall respond `200 OK` with that transaction.

#### BACKEND-003-AC-16 [AUTO]: Not-found, not-owned, and cross-account are all indistinguishable
**Statement**: If `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested for an
`accountId` that does not exist or is not owned by the authenticated user, or for a `transactionId`
that does not exist or does not belong to that account, then the `TransactionController` shall
respond `404 Not Found` in every case.

**Rationale**: Extends `BACKEND-002-AC-16`'s uniform-failure principle across two ids at once — a
`transactionId` that genuinely exists but hangs off a *different* account must read identically to
"doesn't exist," the same way a different user's account id does. Without this, trying
`transactionId`s against accounts the caller doesn't own would leak which transaction ids are real.

#### BACKEND-003-AC-11e [AUTO]: A supplied-but-invalid accountId is a 400, not a 404 or 500 (view)
**Statement**: If `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested with an
`accountId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request`.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family.

#### BACKEND-003-AC-11f [AUTO]: An absent accountId is a 404, not a 400 or 500 (view)
**Statement**: If `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested with an
`accountId` path segment that is empty or blank, then the `TransactionController` shall respond
`404 Not Found`.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family.

#### BACKEND-003-AC-17a [AUTO]: A supplied-but-invalid transactionId is a 400, not a 404 or 500
**Statement**: If `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested with a
`transactionId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request`.

**Rationale**: Same underlying mechanism as `BACKEND-002-AC-24a`/`BACKEND-003-AC-11a`, applied to
the second, independent `@PathVariable UUID transactionId` — Spring converts each path variable
separately, so an unconvertible `transactionId` fails identically regardless of which segment it
is. No new code: the same `MethodArgumentTypeMismatchException` handler already covers it.
`BACKEND-003-AC-17` is its own family (reused across `GET`/`PATCH`/`DELETE` — `AC-17a`/`b` here,
continuing through `AC-17f` by Requirement 6), distinct from the `AC-11` family because it covers a
different path segment.

#### BACKEND-003-AC-17b [AUTO]: An absent transactionId is a 404, not a 400 or 500
**Statement**: If `GET /api/accounts/{accountId}/transactions/{transactionId}` is requested with a
`transactionId` path segment that is empty or blank, then the `TransactionController` shall respond
`404 Not Found`.

**Rationale**: Same mechanism as `BACKEND-002-AC-24b`/`BACKEND-003-AC-11b`, applied to the
`transactionId` segment.

### Requirement 5: Update a transaction

**User story**: As a user, I want to correct a transaction's amount, date, or description after
entering it, without needing to delete and recreate it.

#### BACKEND-003-AC-18 [AUTO]: Valid partial update succeeds and the balance is re-derived
**Statement**: When `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested for
an `accountId` and `transactionId` both owned by the authenticated user, with at least one of
`amount`, `date`, or `description` present and valid, the `TransactionController` shall update only
the field(s) provided, leave any omitted field unchanged, re-derive the owning account's `balance`
by reversing the transaction's previous `amount` and applying its new `amount`, and respond
`200 OK` with the updated transaction.

**Rationale**: `design-decisions.md`'s "Running balance" — editing a transaction must reverse the
old amount's effect and apply the new one (or an equivalent net-delta calculation), under the same
concurrency-safety requirement as creation (Requirement 7). This is a genuine partial update
(`PATCH`, not `PUT`), mirroring `BACKEND-002-AC-17`'s reasoning.

#### BACKEND-003-AC-19 [AUTO]: Not-found, not-owned, and cross-account are all indistinguishable
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested for
an `accountId` that does not exist or is not owned by the authenticated user, or for a
`transactionId` that does not exist or does not belong to that account, then the
`TransactionController` shall respond `404 Not Found` in every case, and shall not modify any
transaction.

**Rationale**: Same uniform-failure principle as `BACKEND-003-AC-16`, applied to update.

#### BACKEND-003-AC-20 [AUTO]: An update with no fields provided is rejected
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
`amount`, `date`, and `description` all absent from the request body, then the
`TransactionController` shall respond `400 Bad Request` and shall not modify the transaction.

**Rationale**: Direct consequence of `BACKEND-003-AC-18` allowing any subset of the three fields,
mirroring `BACKEND-002-AC-19a` exactly — enforced via a derived `@AssertTrue`-annotated boolean
method on `TransactionUpdateRequest`, and also covering a request that supplies exactly one field
as an explicit JSON `null` (indistinguishable from omitting it once bound), not just a bare `{}`
body.

#### BACKEND-003-AC-21 [AUTO]: A present-but-blank description is rejected on update
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
`description` present in the request body but blank, then the `TransactionController` shall respond
`400 Bad Request` and shall not modify the transaction.

**Rationale**: Mirrors `BACKEND-002-AC-19`'s per-field blank check, adapted to `Transaction`'s one
free-text field.

#### BACKEND-003-AC-22 [AUTO]: A present-but-too-early date is rejected on update
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
`date` present in the request body but earlier than the owning account's `openedOn`, then the
`TransactionController` shall respond `400 Bad Request` and shall not modify the transaction.

**Rationale**: Update-side equivalent of `BACKEND-003-AC-10`'s create-side check — same cross-entity
rule, re-checked against the (possibly different) owning account on every update, not just at
creation.

#### BACKEND-003-AC-23 [AUTO]: Account association is not editable via this endpoint
**Statement**: When `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested
with an account-identifying value present in the request body, the `TransactionController` shall
ignore it and leave the transaction's owning `Account` unchanged, updating only `amount`, `date`,
and/or `description` as provided.

**Rationale**: Scope decision mirroring `BACKEND-002-AC-20`'s pattern — a transaction cannot be
moved between accounts via this endpoint (doing so correctly would mean reversing its amount on the
old account's balance and applying it to the new one, a materially different operation from a plain
field edit). Enforced structurally: `TransactionUpdateRequest` simply has no field for the account
association, so this AC's test asserts the association survives unchanged through a request body
that includes an extra, unrecognised field for it.

#### BACKEND-003-AC-11g [AUTO]: A supplied-but-invalid accountId is a 400, not a 404 or 500 (update)
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
an `accountId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request` and shall not modify any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family.

#### BACKEND-003-AC-11h [AUTO]: An absent accountId is a 404, not a 400 or 500 (update)
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
an `accountId` path segment that is empty or blank, then the `TransactionController` shall respond
`404 Not Found` and shall not modify any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family.

#### BACKEND-003-AC-17c [AUTO]: A supplied-but-invalid transactionId is a 400, not a 404 or 500 (update)
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
a `transactionId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request` and shall not modify any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-17` family.

#### BACKEND-003-AC-17d [AUTO]: An absent transactionId is a 404, not a 400 or 500 (update)
**Statement**: If `PATCH /api/accounts/{accountId}/transactions/{transactionId}` is requested with
a `transactionId` path segment that is empty or blank, then the `TransactionController` shall
respond `404 Not Found` and shall not modify any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-17` family.

### Requirement 6: Delete a transaction

**User story**: As a user, I want to delete a transaction I entered by mistake or no longer want
recorded.

#### BACKEND-003-AC-24 [AUTO]: Deletes an owned transaction and reverses the balance
**Statement**: When `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested
for an `accountId` and `transactionId` both owned by the authenticated user, the
`TransactionController` shall delete that transaction, reverse its `amount` from the owning
account's `balance`, and respond `204 No Content`.

**Rationale**: `design-decisions.md`'s "Running balance" — a delete must reverse that transaction's
amount, under the same concurrency-safety requirement as creation and update (Requirement 7).

#### BACKEND-003-AC-25 [AUTO]: Not-found, not-owned, and cross-account are all indistinguishable
**Statement**: If `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested for
an `accountId` that does not exist or is not owned by the authenticated user, or for a
`transactionId` that does not exist or does not belong to that account, then the
`TransactionController` shall respond `404 Not Found` in every case, and shall not delete any
transaction.

**Rationale**: Same uniform-failure principle as `BACKEND-003-AC-16`/`AC-19`, applied to delete.

#### BACKEND-003-AC-11i [AUTO]: A supplied-but-invalid accountId is a 400, not a 404 or 500 (delete)
**Statement**: If `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested with
an `accountId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request` and shall not delete any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family — its final occurrence.

#### BACKEND-003-AC-11j [AUTO]: An absent accountId is a 404, not a 400 or 500 (delete)
**Statement**: If `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested with
an `accountId` path segment that is empty or blank, then the `TransactionController` shall respond
`404 Not Found` and shall not delete any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-11` family — its final occurrence.

#### BACKEND-003-AC-17e [AUTO]: A supplied-but-invalid transactionId is a 400, not a 404 or 500 (delete)
**Statement**: If `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested with
a `transactionId` path segment that is present but not a valid UUID, then the `TransactionController`
shall respond `400 Bad Request` and shall not delete any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-17` family — its final occurrence.

#### BACKEND-003-AC-17f [AUTO]: An absent transactionId is a 404, not a 400 or 500 (delete)
**Statement**: If `DELETE /api/accounts/{accountId}/transactions/{transactionId}` is requested with
a `transactionId` path segment that is empty or blank, then the `TransactionController` shall
respond `404 Not Found` and shall not delete any transaction.

**Rationale**: Continuation of the `BACKEND-003-AC-17` family — its final occurrence.

### Requirement 7: Concurrency-safe running balance maintenance

**User story**: As the system, I need `Account.balance` to stay numerically correct even when
multiple transaction writes race against the same account, so the stored running balance never
silently drifts from what actually happened.

#### BACKEND-003-AC-26 [MANUAL]: Balance stays correct under concurrent writes
**Statement**: While two or more requests concurrently create, update, or delete a `Transaction`
against the same `Account`, the `TransactionService` shall update that account's `balance` such
that no write's effect on the balance is lost or double-applied.

**Rationale**: `design-decisions.md`'s "Running balance" settles that `balance` is a stored,
transactionally-maintained column (not computed at read time) and flags the concrete lost-update
risk of two overlapping writes, but deliberately leaves the locking mechanism — a pessimistic row
lock (`SELECT ... FOR UPDATE` / JPA's `@Lock(LockModeType.PESSIMISTIC_WRITE)`) versus an optimistic
`@Version` column with retry on `OptimisticLockException` — as a choice for whoever hand-codes
`TransactionService`, per the Working Agreement. This spec states the correctness requirement, not
the mechanism. **Verified manually for now**: human code review of the chosen locking strategy
against this requirement at review time, plus a manual race test (firing several concurrent
create/update/delete requests at the same account and confirming the final `balance` matches the
expected sum, e.g. via a quick script or parallel API client calls). **Route to automating later**:
once `TransactionService` exists, a Spock integration spec driving several threads/
`CompletableFuture`s at the same `Account` concurrently and asserting the final `balance` is exactly
right would upgrade this to `[AUTO]` — not attempted in this spec's initial skeleton, since
reliably provoking the race deterministically in a test is itself a nontrivial exercise best left
to the human's own TDD process.

### Requirement 8: Transactions endpoints inherit authentication, not reinvent it

**User story**: As the system, I need every `/api/accounts/.../transactions` route to stay behind
the existing JWT-authentication gate by default, so this spec never needs to add new
security-filter plumbing of its own.

#### BACKEND-003-AC-27 [AUTO]: Unauthenticated requests are rejected before reaching the controller
**Statement**: When any `/api/accounts/{accountId}/transactions` endpoint is requested without a
valid, unexpired access-token cookie, the existing `SecurityConfig`/`JwtAuthenticationFilter` chain
(`AUTH-001-AC-20` through `AUTH-001-AC-23`) shall respond `401 Unauthorized` without
`TransactionController` ever being invoked.

**Rationale**: Confirms this spec doesn't accidentally add a `permitAll` rule for any transactions
route — `SecurityConfig`'s existing `"/api/**".authenticated()` default already covers it, the same
regression-guard role `BACKEND-002-AC-23` served for accounts.

## Cross-references

| Contract                                                                                                                        | Defined here                                                         | Used by                                                                                                                                                                     |
|-----------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Transaction` entity (`amount`, `date`, `description`, `Account` FK)                                                            | This spec                                                            | Build order step 3 (transaction classification engine — future), `frontend_spec_003_transactions.md` (future)                                                             |
| `POST`/`GET`/`GET`/`PATCH`/`DELETE` on `/api/accounts/{accountId}/transactions[/{transactionId}]`                              | This spec                                                            | `frontend_spec_003_transactions.md` (future)                                                                                                                               |
| `Account` entity, `balance` column, `openedOn` field                                                                            | `backend_spec_002_accounts.md` (`BACKEND-002-AC-01` through `AC-06`) | This spec (every create/update/delete mutates `balance`; `openedOn` bounds `date`, `BACKEND-003-AC-10`/`AC-22`)                                                            |
| Owner-scoping via `@AuthenticationPrincipal String userId` / `JwtAuthenticationFilter`                                          | `backend_spec_001_basic_login.md` (`AUTH-001-AC-20` through `AC-23`) | This spec (every endpoint, via the owning `Account`)                                                                                                                       |
| `GlobalExceptionHandler` / custom-exception pattern, `AccountNotFoundException` precedent                                       | `backend_spec_002_accounts.md`                                      | This spec (adds a `TransactionNotFoundException` mapped to `404`)                                                                                                          |
| Malformed/absent id handling (`MethodArgumentTypeMismatchException` → `400`, `NoResourceFoundException`/`MissingPathVariableException` → `404`) | `backend_spec_002_accounts.md` (`BACKEND-002-AC-24a` through `AC-24f`) | This spec (`BACKEND-003-AC-11a..j` for `accountId`, `BACKEND-003-AC-17a..f` for `transactionId` — same handlers, no new code, only new test coverage per path segment per verb) |
| Running balance decision + concurrency-safety note (pessimistic vs. optimistic locking)                                        | `.claude/02-accounts-and-transactions/design-decisions.md` ("Running balance") | This spec (Requirement 7, `BACKEND-003-AC-26`)                                                                                                                             |
| Transaction field decisions (signed amount, date ≥ `openedOn`, free-text description, no currency/category column)             | `.claude/02-accounts-and-transactions/design-decisions.md` ("Transaction fields") | This spec                                                                                                                                                                   |
| Deferred concepts (credit cards, investment/pension accounts, foreign-currency flag, sensitive account identifiers, archiving) | `.claude/02-accounts-and-transactions/spec_candidates.md`            | N/A — confirms these aren't part of this spec                                                                                                                              |

## Acceptance Criteria Summary

- [ ] BACKEND-003-AC-01 [AUTO]: `Transaction` rejects persistence with a null `amount`
- [ ] BACKEND-003-AC-02 [AUTO]: `Transaction` rejects persistence with a null `date`
- [ ] BACKEND-003-AC-03 [AUTO]: `Transaction` rejects persistence with a null or blank `description`
- [ ] BACKEND-003-AC-04 [AUTO]: `Transaction` persistence fails with no owning `Account` set
- [ ] BACKEND-003-AC-05 [AUTO]: `transactions` table schema matches entity mapping at startup
- [ ] BACKEND-003-AC-06 [AUTO]: Valid create returns 201 with the created transaction, balance increased by `amount`
- [ ] BACKEND-003-AC-07 [AUTO]: Create returns 404 uniformly for not-found/not-owned account, no transaction created
- [ ] BACKEND-003-AC-08 [AUTO]: Missing/invalid `amount` on create returns 400, no transaction created
- [ ] BACKEND-003-AC-09 [AUTO]: Missing/blank `description` on create returns 400, no transaction created
- [ ] BACKEND-003-AC-10 [AUTO]: Missing or too-early `date` on create returns 400, no transaction created
- [ ] BACKEND-003-AC-11a [AUTO]: Create returns 400 for a supplied-but-invalid `accountId`
- [ ] BACKEND-003-AC-11b [AUTO]: Create returns 404 for an absent `accountId`
- [ ] BACKEND-003-AC-12 [AUTO]: List returns every transaction for an owned account, 200
- [ ] BACKEND-003-AC-13 [AUTO]: List returns an empty list when the account has no transactions
- [ ] BACKEND-003-AC-14 [AUTO]: List returns 404 uniformly for not-found/not-owned account
- [ ] BACKEND-003-AC-11c [AUTO]: List returns 400 for a supplied-but-invalid `accountId`
- [ ] BACKEND-003-AC-11d [AUTO]: List returns 404 for an absent `accountId`
- [ ] BACKEND-003-AC-15 [AUTO]: View returns an owned transaction, 200
- [ ] BACKEND-003-AC-16 [AUTO]: View returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction
- [ ] BACKEND-003-AC-11e [AUTO]: View returns 400 for a supplied-but-invalid `accountId`
- [ ] BACKEND-003-AC-11f [AUTO]: View returns 404 for an absent `accountId`
- [ ] BACKEND-003-AC-17a [AUTO]: View returns 400 for a supplied-but-invalid `transactionId`
- [ ] BACKEND-003-AC-17b [AUTO]: View returns 404 for an absent `transactionId`
- [ ] BACKEND-003-AC-18 [AUTO]: Valid update updates any subset of `amount`/`date`/`description`, returns 200, re-derives balance
- [ ] BACKEND-003-AC-19 [AUTO]: Update returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction, no modification
- [ ] BACKEND-003-AC-20 [AUTO]: Update with all fields absent returns 400, no modification
- [ ] BACKEND-003-AC-21 [AUTO]: Present-but-blank `description` on update returns 400, no modification
- [ ] BACKEND-003-AC-22 [AUTO]: Present-but-too-early `date` on update returns 400, no modification
- [ ] BACKEND-003-AC-23 [AUTO]: Account association is not editable via the update endpoint
- [ ] BACKEND-003-AC-11g [AUTO]: Update returns 400 for a supplied-but-invalid `accountId`, no modification
- [ ] BACKEND-003-AC-11h [AUTO]: Update returns 404 for an absent `accountId`, no modification
- [ ] BACKEND-003-AC-17c [AUTO]: Update returns 400 for a supplied-but-invalid `transactionId`, no modification
- [ ] BACKEND-003-AC-17d [AUTO]: Update returns 404 for an absent `transactionId`, no modification
- [ ] BACKEND-003-AC-24 [AUTO]: Delete removes an owned transaction, 204, reverses balance
- [ ] BACKEND-003-AC-25 [AUTO]: Delete returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction, no deletion
- [ ] BACKEND-003-AC-11i [AUTO]: Delete returns 400 for a supplied-but-invalid `accountId`, no deletion
- [ ] BACKEND-003-AC-11j [AUTO]: Delete returns 404 for an absent `accountId`, no deletion
- [ ] BACKEND-003-AC-17e [AUTO]: Delete returns 400 for a supplied-but-invalid `transactionId`, no deletion
- [ ] BACKEND-003-AC-17f [AUTO]: Delete returns 404 for an absent `transactionId`, no deletion
- [ ] BACKEND-003-AC-26 [MANUAL]: `Account.balance` stays correct under concurrent create/update/delete writes
- [ ] BACKEND-003-AC-27 [AUTO]: Unauthenticated requests to any transactions route are rejected 401 before the controller runs

## Test case sketches

Per the Working Agreement, these are scenario + block-structure sketches only — `backend-dev` turns
each into a label-only Spock skeleton; the human fills in real `given`/`when`/`then` content.

```groovy
def "BACKEND-003-AC-01: rejects a null amount"() {
    given: "a new Transaction with a null amount"

    when: "the transaction is persisted"

    then: "persistence fails validation"
}
```

```groovy
def "BACKEND-003-AC-01: accepts both a positive and a negative amount"() {
    given: "a new Transaction with a positive amount, and a separate one with a negative amount"

    when: "each transaction is persisted"

    then: "both persist successfully with their given amount"
}
```

```groovy
def "BACKEND-003-AC-02: rejects a null date"() {
    given: "a new Transaction with a null date"

    when: "the transaction is persisted"

    then: "persistence fails validation"
}
```

```groovy
def "BACKEND-003-AC-03: rejects a null or blank description"() {
    given: "a new Transaction with a null or blank description"

    when: "the transaction is persisted"

    then: "persistence fails validation"
}
```

```groovy
def "BACKEND-003-AC-04: rejects persistence with no owning account set"() {
    given: "a new Transaction with no Account set"

    when: "the transaction is persisted"

    then: "persistence fails"
}
```

```groovy
def "BACKEND-003-AC-05: application context loads with a schema-valid transactions table"() {
    given: "the Flyway migration for the transactions table has run"

    when: "the Spring application context loads"

    then: "startup succeeds with no Hibernate schema-validation error"
}
```

```groovy
def "BACKEND-003-AC-06: creates a transaction and increases the account's balance"() {
    given: "an authenticated user, an owned account, and a valid transaction creation request"

    when: "POST /api/accounts/{accountId}/transactions is requested"

    then: "the response is 201 Created with the created transaction"

    and: "the account's balance is increased by the transaction's amount"
}
```

```groovy
def "BACKEND-003-AC-07: returns 404 uniformly for a non-existent or non-owned account on create"() {
    given: "either a non-existent account id, or an account id owned by a different user"

    when: "POST /api/accounts/{accountId}/transactions is requested with that id"

    then: "the response is 404 Not Found in both cases"

    and: "no transaction is created"
}
```

```groovy
def "BACKEND-003-AC-08: rejects creation with a missing or invalid amount"() {
    given: "a creation request with either a missing amount or a non-numeric amount value"

    when: "POST /api/accounts/{accountId}/transactions is requested"

    then: "the response is 400 Bad Request"

    and: "no transaction is created"
}
```

```groovy
def "BACKEND-003-AC-09: rejects creation with a missing or blank description"() {
    given: "a creation request with a missing or blank description"

    when: "POST /api/accounts/{accountId}/transactions is requested"

    then: "the response is 400 Bad Request"

    and: "no transaction is created"
}
```

```groovy
def "BACKEND-003-AC-10: rejects creation with a missing or too-early date"() {
    given: "a creation request with either a missing date, or a date earlier than the account's openedOn"

    when: "POST /api/accounts/{accountId}/transactions is requested"

    then: "the response is 400 Bad Request"

    and: "no transaction is created"
}
```

```groovy
def "BACKEND-003-AC-11a: returns 400 for a supplied-but-invalid accountId on create"() {
    given: "an authenticated user"

    when: "POST /api/accounts/{accountId}/transactions is requested with an accountId that is not a UUID"

    then: "the response is 400 Bad Request"
}
```

```groovy
def "BACKEND-003-AC-11b: returns 404 for an absent accountId on create"() {
    given: "an authenticated user"

    when: "POST /api/accounts/{accountId}/transactions is requested with an accountId that is empty or blank"

    then: "the response is 404 Not Found"
}
```

```groovy
def "BACKEND-003-AC-12: lists every transaction for an owned account"() {
    given: "an account owned by the authenticated user with several transactions recorded against it"

    when: "GET /api/accounts/{accountId}/transactions is requested with that account's id"

    then: "the response is 200 OK containing every transaction for that account"
}
```

```groovy
def "BACKEND-003-AC-13: returns an empty list for an owned account with no transactions"() {
    given: "an account owned by the authenticated user with no transactions"

    when: "GET /api/accounts/{accountId}/transactions is requested with that account's id"

    then: "the response is 200 OK with an empty list"
}
```

```groovy
def "BACKEND-003-AC-14: returns 404 uniformly for a non-existent or non-owned account on list"() {
    given: "either a non-existent account id, or an account id owned by a different user"

    when: "GET /api/accounts/{accountId}/transactions is requested with that id"

    then: "the response is 404 Not Found in both cases"
}
```

```groovy
def "BACKEND-003-AC-11c: returns 400 for a supplied-but-invalid accountId on list"() {
    given: "an authenticated user"

    when: "GET /api/accounts/{accountId}/transactions is requested with an accountId that is not a UUID"

    then: "the response is 400 Bad Request"
}
```

```groovy
def "BACKEND-003-AC-11d: returns 404 for an absent accountId on list"() {
    given: "an authenticated user"

    when: "GET /api/accounts/{accountId}/transactions is requested with an accountId that is empty or blank"

    then: "the response is 404 Not Found"
}
```

```groovy
def "BACKEND-003-AC-15: returns a single owned transaction"() {
    given: "a transaction owned (via its account) by the authenticated user"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

    then: "the response is 200 OK with that transaction"
}
```

```groovy
def "BACKEND-003-AC-16: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction"() {
    given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

    then: "the response is 404 Not Found in every case"
}
```

```groovy
def "BACKEND-003-AC-11e: returns 400 for a supplied-but-invalid accountId on view"() {
    given: "an authenticated user"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

    then: "the response is 400 Bad Request"
}
```

```groovy
def "BACKEND-003-AC-11f: returns 404 for an absent accountId on view"() {
    given: "an authenticated user"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

    then: "the response is 404 Not Found"
}
```

```groovy
def "BACKEND-003-AC-17a: returns 400 for a supplied-but-invalid transactionId on view"() {
    given: "an authenticated user and an owned account"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

    then: "the response is 400 Bad Request"
}
```

```groovy
def "BACKEND-003-AC-17b: returns 404 for an absent transactionId on view"() {
    given: "an authenticated user and an owned account"

    when: "GET /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

    then: "the response is 404 Not Found"
}
```

```groovy
def "BACKEND-003-AC-18: updates an owned transaction's amount, date, or description and re-derives the balance"() {
    given: "a transaction owned (via its account) by the authenticated user, and an update request with at least one of amount, date, or description present"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

    then: "the response is 200 OK with the updated transaction"

    and: "any field omitted from the request is unchanged on the transaction"

    and: "the account's balance reflects the old amount reversed and the new amount applied"
}
```

```groovy
def "BACKEND-003-AC-19: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction on update"() {
    given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

    then: "the response is 404 Not Found in every case"

    and: "no transaction is modified"
}
```

```groovy
def "BACKEND-003-AC-20: rejects an update with no fields provided"() {
    given: "a transaction owned by the authenticated user and an update request with amount, date, and description all absent"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

    then: "the response is 400 Bad Request"

    and: "the transaction is not modified"
}
```

```groovy
def "BACKEND-003-AC-21: rejects an update with a present-but-blank description"() {
    given: "a transaction owned by the authenticated user and an update request where description is present but blank"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

    then: "the response is 400 Bad Request"

    and: "the transaction is not modified"
}
```

```groovy
def "BACKEND-003-AC-22: rejects an update with a present-but-too-early date"() {
    given: "a transaction owned by the authenticated user and an update request where date is present but earlier than the account's openedOn"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

    then: "the response is 400 Bad Request"

    and: "the transaction is not modified"
}
```

```groovy
def "BACKEND-003-AC-23: leaves the account association unchanged on update"() {
    given: "a transaction owned by the authenticated user and an update request body that also includes an account-identifying value"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested"

    then: "the response is 200 OK"

    and: "the transaction's owning account is unchanged from before the request"
}
```

```groovy
def "BACKEND-003-AC-11g: returns 400 for a supplied-but-invalid accountId on update"() {
    given: "an authenticated user"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

    then: "the response is 400 Bad Request"

    and: "no transaction is modified"
}
```

```groovy
def "BACKEND-003-AC-11h: returns 404 for an absent accountId on update"() {
    given: "an authenticated user"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

    then: "the response is 404 Not Found"

    and: "no transaction is modified"
}
```

```groovy
def "BACKEND-003-AC-17c: returns 400 for a supplied-but-invalid transactionId on update"() {
    given: "an authenticated user and an owned account"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

    then: "the response is 400 Bad Request"

    and: "no transaction is modified"
}
```

```groovy
def "BACKEND-003-AC-17d: returns 404 for an absent transactionId on update"() {
    given: "an authenticated user and an owned account"

    when: "PATCH /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

    then: "the response is 404 Not Found"

    and: "no transaction is modified"
}
```

```groovy
def "BACKEND-003-AC-24: deletes an owned transaction and reverses the balance"() {
    given: "a transaction owned (via its account) by the authenticated user"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with those ids"

    then: "the response is 204 No Content"

    and: "the transaction no longer exists"

    and: "the account's balance has the transaction's amount reversed out of it"
}
```

```groovy
def "BACKEND-003-AC-25: returns 404 uniformly for not-found/not-owned account or cross-account/not-found transaction on delete"() {
    given: "a non-existent or non-owned accountId, or a transactionId that does not exist or belongs to a different account"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with that combination"

    then: "the response is 404 Not Found in every case"

    and: "no transaction is deleted"
}
```

```groovy
def "BACKEND-003-AC-11i: returns 400 for a supplied-but-invalid accountId on delete"() {
    given: "an authenticated user"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is not a UUID"

    then: "the response is 400 Bad Request"

    and: "no transaction is deleted"
}
```

```groovy
def "BACKEND-003-AC-11j: returns 404 for an absent accountId on delete"() {
    given: "an authenticated user"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with an accountId that is empty or blank"

    then: "the response is 404 Not Found"

    and: "no transaction is deleted"
}
```

```groovy
def "BACKEND-003-AC-17e: returns 400 for a supplied-but-invalid transactionId on delete"() {
    given: "an authenticated user and an owned account"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is not a UUID"

    then: "the response is 400 Bad Request"

    and: "no transaction is deleted"
}
```

```groovy
def "BACKEND-003-AC-17f: returns 404 for an absent transactionId on delete"() {
    given: "an authenticated user and an owned account"

    when: "DELETE /api/accounts/{accountId}/transactions/{transactionId} is requested with a transactionId that is empty or blank"

    then: "the response is 404 Not Found"

    and: "no transaction is deleted"
}
```

```groovy
def "BACKEND-003-AC-26: balance stays correct under concurrent create/update/delete writes [MANUAL]"() {
    given: "an owned account and several concurrent create/update/delete requests racing against it"

    when: "the requests are fired concurrently"

    then: "the account's final balance equals the expected sum, with no lost or double-applied update"

    // MANUAL verification for now: human review of the chosen locking strategy plus a manual
    // concurrent-request check, per BACKEND-003-AC-26's rationale. Not a Spock skeleton yet.
}
```

```groovy
def "BACKEND-003-AC-27: rejects an unauthenticated request before the controller runs"() {
    given: "a request to a /api/accounts/{accountId}/transactions endpoint with no valid access-token cookie"

    when: "the request is made"

    then: "the response is 401 Unauthorized"

    and: "TransactionController is never invoked"
}
```

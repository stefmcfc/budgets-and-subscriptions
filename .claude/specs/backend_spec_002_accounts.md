# Accounts (Backend)

**Status**: In progress (AC-01 through AC-12 done, 2026-10-26)
**Priority**: P1 — first spec of build order step 2 ("Accounts and transactions"); every
account-scoped feature (transactions, spending insights, multi-person balances) depends on an
`Account` existing to attach to.
**Depends on**: `backend_spec_001_basic_login.md` (JWT-authenticated principal via
`JwtAuthenticationFilter`/`SecurityContextHolder`, the `User` entity as the owner FK target, and
the `GlobalExceptionHandler`/custom-exception pattern this spec extends)
**Area**: Backend

## Overview

This delivers the `Account` entity and its full CRUD surface: a plain ledger account
(`CURRENT`, `SAVINGS`, or `CASH_ISA`) with a `bankName` and `accountName` (split, not one combined
field, so accounts can be grouped by bank later without parsing free text — see
`design-decisions.md`'s 2026-10-23 revision), an `openingBalance`/`openedOn` pair that lets
a user add an account that already has money in it without re-entering its whole history, and a
stored `balance` column seeded from `openingBalance` at creation. Every account belongs to exactly
one owning `User`; every endpoint in this spec scopes its query or mutation to the authenticated
principal's own accounts, reusing the JWT-authentication plumbing `backend_spec_001_basic_login.md`
already built rather than adding any new security-filter machinery. This is deliberately the
`Account` entity alone — no `Transaction`, no category/classification concept, and no
concurrency-safe balance *mutation* logic, all of which are `backend_spec_003_transactions.md`'s
job once an account exists to create transactions against. Full rationale for every field and
scope boundary below lives in `.claude/02-accounts-and-transactions/design-decisions.md`; deferred
account types and concepts (credit cards, investment/pension/Stocks & Shares ISA, encrypted
sort-code/account-number fields, archiving) are tracked in that stage's `spec_candidates.md`, not
re-litigated here.

## Requirements

### Requirement 1: `Account` entity and schema

**User story**: As the system, I need a durable, owner-scoped account record with a seeded
running balance so that later transaction and reporting features have something correct to
operate on.

#### BACKEND-002-AC-01 [AUTO]: Required bank name and account name
**Statement**: The `Account` entity shall reject persistence of an account with a null or blank
`bankName` or `accountName`.

**Rationale**: `design-decisions.md`'s "Account fields" — both are free text but required.

#### BACKEND-002-AC-02 [AUTO]: No uniqueness constraint on bank name or account name
**Statement**: The `Account` entity shall allow two accounts owned by the same user to share the
same `bankName`, the same `accountName`, or both, such that persisting a second account with
either (or both) duplicated succeeds.

**Rationale**: `design-decisions.md`'s "Account fields" explicitly rules out a uniqueness
constraint on either field — a user can have two accounts at the same bank, two accounts with the
same account-type label at different banks, or even an identical pair of both.

#### BACKEND-002-AC-03 [AUTO]: Type restricted to the v1 enum
**Statement**: The `Account` entity shall store `type` as exactly one of `CURRENT`, `SAVINGS`, or
`CASH_ISA`.

**Rationale**: `design-decisions.md`'s "Account types (v1)" — credit card, investment, pension,
and Stocks & Shares ISA types are explicitly deferred (`spec_candidates.md`), not values this
enum carries yet.

#### BACKEND-002-AC-04 [AUTO]: Owner required
**Statement**: If an `Account` is persisted with no owner set, then persistence shall fail.

**Rationale**: `design-decisions.md`'s "Account fields" — every account belongs to exactly one
user; there is no ownerless or shared-account concept in this stage.

#### BACKEND-002-AC-05 [AUTO]: Balance seeded from opening balance at creation
**Statement**: When a new `Account` is constructed, the `Account` entity shall initialize
`balance` equal to the given `openingBalance`.

**Rationale**: `design-decisions.md`'s "Running balance" — `balance` is a stored column, not
computed on the fly, and starts equal to `openingBalance` at creation. Keeping it correct under
concurrent transaction writes is `backend_spec_003_transactions.md`'s concern; this AC covers only
this entity's own seeding behaviour at construction.

#### BACKEND-002-AC-06 [AUTO]: Schema matches entity mapping
**Statement**: When the Spring application context loads, the `accounts` table created by its
Flyway migration shall match the `Account` entity's JPA mapping, such that startup succeeds under
`ddl-auto: validate`.

**Rationale**: Same enforcement mechanism as `AUTH-001-AC-04` — schema changes come from a Flyway
migration, never Hibernate auto-DDL.

### Requirement 2: Create an account

**User story**: As a user, I want to add a new account with its bank name, account name, type,
opening balance, and the date that balance was true, so that I can start tracking it without
re-entering its whole transaction history.

#### BACKEND-002-AC-07 [AUTO]: Valid creation returns the new account
**Statement**: When `POST /api/accounts` is requested by an authenticated user with a valid
`bankName`, `accountName`, `type`, `openingBalance`, and `openedOn`, the `AccountController` shall
create a new `Account` owned by that user, with `balance` equal to `openingBalance`, and respond
`201 Created` with the created account.

**Rationale**: The core create flow; surfaces `BACKEND-002-AC-05`'s balance-seeding behaviour at
the API boundary.

#### BACKEND-002-AC-08 [AUTO]: Owner always derived from the authenticated principal
**Statement**: The `AccountController` shall derive a created account's owner solely from the
authenticated principal, such that any owner or user-id field present in the
`POST /api/accounts` request body is ignored.

**Rationale**: Mirrors the project's existing pattern of never trusting a client-supplied identity
field (registration always sets `role=USER` server-side, never from the request body) — prevents
one user from creating an account under another user's ownership.

#### BACKEND-002-AC-09 [AUTO]: Blank or missing bank name or account name rejected
**Statement**: If `POST /api/accounts` is requested with a blank or missing `bankName` or
`accountName`, then the `AccountController` shall respond `400 Bad Request` and shall not create
an account.

**Rationale**: API-layer enforcement of `BACKEND-002-AC-01`.

#### BACKEND-002-AC-10 [AUTO]: Missing or invalid type rejected
**Statement**: If `POST /api/accounts` is requested with a missing `type`, or a `type` value
outside `CURRENT`/`SAVINGS`/`CASH_ISA`, then the `AccountController` shall respond
`400 Bad Request` and shall not create an account.

**Rationale**: Covers both a missing field and an unrecognized enum string in one AC, the same
combining pattern `AUTH-001-AC-08` used for registration's email/displayName checks. The
unrecognised-enum-string case fails Jackson deserialization before validation ever runs, so
`GlobalExceptionHandler` needs its own handler for `HttpMessageNotReadableException` mapped to
`400` for this to hold — without it, this case falls through to the generic `Exception` handler
and returns `500` instead. (This exact gap is what `ears_format.md`'s own worked example
describes finding and fixing — worth checking for directly rather than assuming it's already
handled.)

#### BACKEND-002-AC-11 [AUTO]: Missing opening balance rejected
**Statement**: If `POST /api/accounts` is requested with a missing `openingBalance`, then the
`AccountController` shall respond `400 Bad Request` and shall not create an account.

#### BACKEND-002-AC-12 [AUTO]: Missing or future-dated openedOn rejected
**Statement**: If `POST /api/accounts` is requested with a missing `openedOn`, or an `openedOn`
date after today, then the `AccountController` shall respond `400 Bad Request` and shall not
create an account.

**Rationale**: `design-decisions.md`'s "Account fields" — `openedOn` is the date the opening
balance was true as of; a future date has no coherent "starting point" yet, and it is also the
earliest date a `Transaction` can later be recorded against this account.

### Requirement 3: List own accounts

**User story**: As a user, I want to see all of my accounts in one list so I can get an overview
of my finances.

#### BACKEND-002-AC-13 [AUTO]: Lists only the authenticated user's own accounts
**Statement**: When `GET /api/accounts` is requested by an authenticated user, the
`AccountController` shall respond `200 OK` with every `Account` owned by that user, and no
account owned by any other user.

**Rationale**: The core authorisation-scoping requirement for this spec — reuses the existing JWT
principal (`@AuthenticationPrincipal String userId`, the same pattern `AuthController.
getCurrentUser` already uses) rather than any new filter plumbing.

#### BACKEND-002-AC-14 [AUTO]: Empty list when the user has no accounts
**Statement**: When `GET /api/accounts` is requested by an authenticated user who owns no
accounts, the `AccountController` shall respond `200 OK` with an empty list.

**Rationale**: Distinguishes "no accounts yet" from an error condition.

### Requirement 4: View a single account

**User story**: As a user, I want to view one account's full detail.

#### BACKEND-002-AC-15 [AUTO]: Returns an owned account
**Statement**: When `GET /api/accounts/{id}` is requested for an `id` owned by the authenticated
user, the `AccountController` shall respond `200 OK` with that account.

#### BACKEND-002-AC-16 [AUTO]: Not-found and not-owned are indistinguishable
**Statement**: If `GET /api/accounts/{id}` is requested for an `id` that does not exist, or that
belongs to a different user, then the `AccountController` shall respond `404 Not Found` in both
cases.

**Rationale**: Mirrors `AUTH-001-AC-11`'s uniform-failure principle — a distinct response for
"doesn't exist" vs. "exists but isn't yours" would leak which account ids belong to other users.

### Requirement 5: Update an account

**User story**: As a user, I want to rename an account or correct its type after creation,
without needing to delete and recreate it.

#### BACKEND-002-AC-17 [AUTO]: Valid update to bank name, account name, and/or type succeeds
**Statement**: When `PATCH /api/accounts/{id}` is requested for an `id` owned by the authenticated
user with a valid `bankName`, `accountName`, and/or `type`, the `AccountController` shall update
that account and respond `200 OK` with the updated account.

#### BACKEND-002-AC-18 [AUTO]: Not-found and not-owned are indistinguishable
**Statement**: If `PATCH /api/accounts/{id}` is requested for an `id` that does not exist, or that
belongs to a different user, then the `AccountController` shall respond `404 Not Found` in both
cases, and shall not modify any account.

**Rationale**: Same uniform-failure principle as `BACKEND-002-AC-16`.

#### BACKEND-002-AC-19 [AUTO]: Blank bank name or account name rejected on update
**Statement**: If `PATCH /api/accounts/{id}` is requested with a blank `bankName` or `accountName`,
then the `AccountController` shall respond `400 Bad Request` and shall not modify the account.

#### BACKEND-002-AC-20 [AUTO]: openingBalance, openedOn, and balance are not editable via this endpoint
**Statement**: When `PATCH /api/accounts/{id}` is requested with an `openingBalance`, `openedOn`,
or `balance` value present in the request body, the `AccountController` shall leave those fields
unchanged on the account, updating only `bankName`, `accountName`, and/or `type`.

**Rationale**: Scope decision made in this spec, not pre-derived from `design-decisions.md`:
editing the opening-balance/opened-on facts after creation — and correctly re-seeding a *stored*
running balance to match — is a materially harder problem once `backend_spec_003_transactions.md`
lands transactions against this account (the balance would then need re-deriving against existing
transaction history, not just reset). With no transactions able to exist yet in this spec alone,
there's no concrete need to solve that now. A mis-entered opening balance or date is corrected by
deleting and recreating the account (`BACKEND-002-AC-21`) for now. This is enforced structurally —
the `AccountUpdateRequest` DTO simply has no field for `openingBalance`/`openedOn`/`balance` —
rather than by a runtime rejection, so this AC's test asserts those values survive unchanged
through a request body that includes them anyway (e.g. an extra, unrecognised JSON field).

### Requirement 6: Delete an account

**User story**: As a user, I want to delete an account I created by mistake or no longer need.

#### BACKEND-002-AC-21 [AUTO]: Deletes an owned account
**Statement**: When `DELETE /api/accounts/{id}` is requested for an `id` owned by the
authenticated user, the `AccountController` shall delete that account and respond
`204 No Content`.

**Rationale**: This spec's `Account` has no `Transaction` referencing it yet (that FK doesn't
exist until `backend_spec_003_transactions.md`), so a hard delete has nothing to cascade over;
that spec will need its own cascade-vs-restrict decision once the FK exists. Account archiving
(a soft alternative to deletion) is explicitly deferred — see `spec_candidates.md`.

#### BACKEND-002-AC-22 [AUTO]: Not-found and not-owned are indistinguishable
**Statement**: If `DELETE /api/accounts/{id}` is requested for an `id` that does not exist, or
that belongs to a different user, then the `AccountController` shall respond `404 Not Found` in
both cases, and shall not delete any account.

**Rationale**: Same uniform-failure principle as `BACKEND-002-AC-16`/`BACKEND-002-AC-18`.

### Requirement 7: Accounts endpoints inherit authentication, not reinvent it

**User story**: As the system, I need every `/api/accounts` route to stay behind the existing
JWT-authentication gate by default, so this spec never needs to add new security-filter plumbing
of its own.

#### BACKEND-002-AC-23 [AUTO]: Unauthenticated requests are rejected before reaching the controller
**Statement**: When any `/api/accounts` endpoint is requested without a valid, unexpired
access-token cookie, the existing `SecurityConfig`/`JwtAuthenticationFilter` chain
(`AUTH-001-AC-20` through `AUTH-001-AC-23`) shall respond `401 Unauthorized` without
`AccountController` ever being invoked.

**Rationale**: Confirms this spec doesn't accidentally add a `permitAll` rule for any accounts
route — `SecurityConfig`'s existing `"/api/**".authenticated()` default already covers it. This
AC exists as a tested regression guard for that default, not to introduce new filter behaviour.

## Cross-references

| Contract                                                                                                                        | Defined here                                                         | Used by                                                                                                                                                                     |
|---------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Account` entity (`bankName`, `accountName`, `type`, `openingBalance`, `openedOn`, `balance`, owner FK) | This spec                                                   | `backend_spec_003_transactions.md` (future — owns the `Transaction`→`Account` FK and balance mutation), `frontend_spec_002_accounts.md` (future)                            |
| `AccountType` enum (`CURRENT`, `SAVINGS`, `CASH_ISA`)                                                                           | This spec                                                            | `backend_spec_003_transactions.md` (future), frontend account type labels                                                                                                   |
| `POST /api/accounts`, `GET /api/accounts`, `GET /api/accounts/{id}`, `PATCH /api/accounts/{id}`, `DELETE /api/accounts/{id}`    | This spec                                                            | `frontend_spec_002_accounts.md` (future)                                                                                                                                    |
| Owner-scoping via `@AuthenticationPrincipal String userId` / `JwtAuthenticationFilter`                                          | `backend_spec_001_basic_login.md` (`AUTH-001-AC-20` through `AC-23`) | This spec (every endpoint)                                                                                                                                                  |
| `User` entity (owner FK target)                                                                                                 | `backend_spec_001_basic_login.md` (`AUTH-001-AC-01` through `AC-04`) | This spec                                                                                                                                                                   |
| `GlobalExceptionHandler` / custom-exception pattern (`DuplicateEmailException`, `LoginException`, `UserException` as precedent) | `backend_spec_001_basic_login.md`                                    | This spec (adds an account-not-found exception mapped to `404`, and needs an `HttpMessageNotReadableException` handler mapped to `400` per `BACKEND-002-AC-10`'s rationale) |
| Account types/fields/running-balance decisions                                                                                  | `.claude/02-accounts-and-transactions/design-decisions.md`           | This spec                                                                                                                                                                   |
| Deferred account concepts (credit cards, investment/pension/Stocks & Shares ISA, encrypted sort-code/account-number, archiving) | `.claude/02-accounts-and-transactions/spec_candidates.md`            | N/A — confirms these aren't part of this spec                                                                                                                               |

## Acceptance Criteria Summary

- [x] BACKEND-002-AC-01 [AUTO]: `Account` rejects a null or blank `bankName` or `accountName`
- [x] BACKEND-002-AC-02 [AUTO]: `Account` allows duplicate `bankName`/`accountName` for the same owner
- [x] BACKEND-002-AC-03 [AUTO]: `Account.type` is restricted to `CURRENT`/`SAVINGS`/`CASH_ISA`
- [x] BACKEND-002-AC-04 [AUTO]: `Account` persistence fails with no owner set
- [x] BACKEND-002-AC-05 [AUTO]: New `Account`'s `balance` is initialised equal to `openingBalance`
- [x] BACKEND-002-AC-06 [AUTO]: `accounts` table schema matches entity mapping at startup
- [x] BACKEND-002-AC-07 [AUTO]: Valid `POST /api/accounts` creates and returns the account, 201
- [x] BACKEND-002-AC-08 [AUTO]: Created account's owner always comes from the authenticated principal, never the request body
- [x] BACKEND-002-AC-09 [AUTO]: Blank/missing `bankName` or `accountName` on create returns 400, no account created
- [x] BACKEND-002-AC-10 [AUTO]: Missing/invalid `type` on create returns 400, no account created
- [x] BACKEND-002-AC-11 [AUTO]: Missing `openingBalance` on create returns 400, no account created
- [x] BACKEND-002-AC-12 [AUTO]: Missing or future `openedOn` on create returns 400, no account created
- [ ] BACKEND-002-AC-13 [AUTO]: `GET /api/accounts` returns only the authenticated user's own accounts
- [ ] BACKEND-002-AC-14 [AUTO]: `GET /api/accounts` returns an empty list when the user has no accounts
- [ ] BACKEND-002-AC-15 [AUTO]: `GET /api/accounts/{id}` returns an owned account, 200
- [ ] BACKEND-002-AC-16 [AUTO]: `GET /api/accounts/{id}` returns 404 uniformly for not-found and not-owned
- [ ] BACKEND-002-AC-17 [AUTO]: Valid `PATCH /api/accounts/{id}` updates bank name/account name/type, returns 200
- [ ] BACKEND-002-AC-18 [AUTO]: `PATCH /api/accounts/{id}` returns 404 uniformly for not-found and not-owned
- [ ] BACKEND-002-AC-19 [AUTO]: Blank `bankName` or `accountName` on update returns 400, no modification
- [ ] BACKEND-002-AC-20 [AUTO]: `openingBalance`/`openedOn`/`balance` are not modifiable via the update endpoint
- [ ] BACKEND-002-AC-21 [AUTO]: `DELETE /api/accounts/{id}` deletes an owned account, 204
- [ ] BACKEND-002-AC-22 [AUTO]: `DELETE /api/accounts/{id}` returns 404 uniformly for not-found and not-owned
- [ ] BACKEND-002-AC-23 [AUTO]: Unauthenticated requests to any `/api/accounts` route are rejected 401 before the controller runs

## Test case sketches

Per the Working Agreement, these are scenario + block-structure sketches only — `backend-dev`
turns each into a label-only Spock skeleton; the human fills in real `given`/`when`/`then` content.

```groovy
def "BACKEND-002-AC-01: rejects a null or blank bank name or account name"() {
    given: "a new Account with a null or blank bankName or accountName"

    when: "the account is persisted"

    then: "persistence fails validation"
}
```

```groovy
def "BACKEND-002-AC-02: allows two accounts with the same bank name/account name for the same owner"() {
    given: "a persisted account owned by a user with a given bankName and accountName"

    when: "a second account with the same bankName and/or accountName and owner is persisted"

    then: "persistence succeeds for both accounts"
}
```

```groovy
def "BACKEND-002-AC-03: accepts each of the three v1 account types"() {
    given: "a new Account for each of CURRENT, SAVINGS, and CASH_ISA"

    when: "each account is persisted"

    then: "all three persist successfully with their given type"
}
```

```groovy
def "BACKEND-002-AC-04: rejects persistence with no owner set"() {
    given: "a new Account with no owner set"

    when: "the account is persisted"

    then: "persistence fails"
}
```

```groovy
def "BACKEND-002-AC-05: seeds balance equal to opening balance at construction"() {
    given: "a given openingBalance value"

    when: "a new Account is constructed with that openingBalance"

    then: "the account's balance equals the given openingBalance"
}
```

```groovy
def "BACKEND-002-AC-06: application context loads with a schema-valid accounts table"() {
    given: "the Flyway migration for the accounts table has run"

    when: "the Spring application context loads"

    then: "startup succeeds with no Hibernate schema-validation error"
}
```

```groovy
def "BACKEND-002-AC-07: creates and returns a new account for the authenticated user"() {
    given: "an authenticated user and a valid account creation request"

    when: "POST /api/accounts is requested"

    then: "the response is 201 Created with the created account"

    and: "the created account's balance equals the given openingBalance"

    and: "the created account is owned by the authenticated user"
}
```

```groovy
def "BACKEND-002-AC-08: ignores a client-supplied owner id"() {
    given: "an authenticated user and a creation request body containing a different user's id as owner"

    when: "POST /api/accounts is requested"

    then: "the created account's owner is the authenticated user, not the id from the request body"
}
```

```groovy
def "BACKEND-002-AC-09: rejects account creation with a blank or missing bank name or account name"() {
    given: "a creation request with a blank or missing bankName or accountName"

    when: "POST /api/accounts is requested"

    then: "the response is 400 Bad Request"

    and: "no account is created"
}
```

```groovy
def "BACKEND-002-AC-10: rejects account creation with a missing or invalid type"() {
    given: "a creation request with either a missing type or a type value outside the allowed enum"

    when: "POST /api/accounts is requested"

    then: "the response is 400 Bad Request"

    and: "no account is created"
}
```

```groovy
def "BACKEND-002-AC-11: rejects account creation with a missing opening balance"() {
    given: "a creation request with a missing openingBalance"

    when: "POST /api/accounts is requested"

    then: "the response is 400 Bad Request"

    and: "no account is created"
}
```

```groovy
def "BACKEND-002-AC-12: rejects account creation with a missing or future-dated openedOn"() {
    given: "a creation request with either a missing openedOn or an openedOn after today"

    when: "POST /api/accounts is requested"

    then: "the response is 400 Bad Request"

    and: "no account is created"
}
```

```groovy
def "BACKEND-002-AC-13: lists only the authenticated user's own accounts"() {
    given: "two users, each owning at least one account"

    when: "GET /api/accounts is requested by the first user"

    then: "the response is 200 OK containing only the first user's accounts"
}
```

```groovy
def "BACKEND-002-AC-14: returns an empty list for a user with no accounts"() {
    given: "an authenticated user who owns no accounts"

    when: "GET /api/accounts is requested"

    then: "the response is 200 OK with an empty list"
}
```

```groovy
def "BACKEND-002-AC-15: returns a single owned account"() {
    given: "an account owned by the authenticated user"

    when: "GET /api/accounts/{id} is requested with that account's id"

    then: "the response is 200 OK with that account"
}
```

```groovy
def "BACKEND-002-AC-16: returns 404 uniformly for a non-existent or non-owned account"() {
    given: "either a non-existent account id, or an account id owned by a different user"

    when: "GET /api/accounts/{id} is requested with that id"

    then: "the response is 404 Not Found in both cases"
}
```

```groovy
def "BACKEND-002-AC-17: updates an owned account's bank name, account name, and/or type"() {
    given: "an account owned by the authenticated user and a valid update request"

    when: "PATCH /api/accounts/{id} is requested with that account's id"

    then: "the response is 200 OK with the updated account"
}
```

```groovy
def "BACKEND-002-AC-18: returns 404 uniformly for a non-existent or non-owned account on update"() {
    given: "either a non-existent account id, or an account id owned by a different user"

    when: "PATCH /api/accounts/{id} is requested with that id"

    then: "the response is 404 Not Found in both cases"

    and: "no account is modified"
}
```

```groovy
def "BACKEND-002-AC-19: rejects an update with a blank bank name or account name"() {
    given: "an account owned by the authenticated user and an update request with a blank bankName or accountName"

    when: "PATCH /api/accounts/{id} is requested"

    then: "the response is 400 Bad Request"

    and: "the account is not modified"
}
```

```groovy
def "BACKEND-002-AC-20: leaves openingBalance, openedOn, and balance unchanged on update"() {
    given: "an account owned by the authenticated user and an update request body that also includes openingBalance, openedOn, and balance values"

    when: "PATCH /api/accounts/{id} is requested"

    then: "the response is 200 OK"

    and: "the account's openingBalance, openedOn, and balance are unchanged from before the request"

    and: "only bankName, accountName, and/or type reflect the request"
}
```

```groovy
def "BACKEND-002-AC-21: deletes an owned account"() {
    given: "an account owned by the authenticated user"

    when: "DELETE /api/accounts/{id} is requested with that account's id"

    then: "the response is 204 No Content"

    and: "the account no longer exists"
}
```

```groovy
def "BACKEND-002-AC-22: returns 404 uniformly for a non-existent or non-owned account on delete"() {
    given: "either a non-existent account id, or an account id owned by a different user"

    when: "DELETE /api/accounts/{id} is requested with that id"

    then: "the response is 404 Not Found in both cases"

    and: "no account is deleted"
}
```

```groovy
def "BACKEND-002-AC-23: rejects an unauthenticated request before the controller runs"() {
    given: "a request to an /api/accounts endpoint with no valid access-token cookie"

    when: "the request is made"

    then: "the response is 401 Unauthorized"

    and: "AccountController is never invoked"
}
```

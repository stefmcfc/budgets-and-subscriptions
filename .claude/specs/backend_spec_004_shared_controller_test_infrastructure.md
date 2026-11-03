# Shared Controller-Spec Auth Test Infrastructure (Backend)

**Status**: In progress
**Priority**: P2 — test-infrastructure cleanup, no user-facing behaviour change; nothing else
depends on this existing, but it's cheapest to do before `TransactionControllerSpec` (
`backend_spec_003_transactions.md`'s `BACKEND-003-AC-27`) gets written, so that spec starts from
the shared pattern rather than yet another inline copy of it.
**Depends on**:
- `backend_spec_001_basic_login.md` (`AUTH-001-AC-15`, the `AuthControllerSpec` test this
  consolidates; `AUTH-001-AC-20` through `AC-22`, whose `JwtAuthenticationFilterSpec` coverage
  with a *real* `JwtService` is the reason the mocked-`JwtService` layer no longer needs to
  distinguish malformed/expired/invalid-signature cookies itself)
- `backend_spec_002_accounts.md` (`BACKEND-002-AC-23`, the five `AccountControllerSpec` tests —
  create/list/view/update/delete — this consolidates)
- `backend_spec_003_transactions.md` (`BACKEND-003-AC-27`, not yet implemented — the motivating
  reason to land this before `TransactionControllerSpec` is written)
**Area**: Backend

## Overview

`AccountControllerSpec` and `AuthControllerSpec` each independently grew the same three things:
their own `@Autowired MockMvc mockMvc` and `@SpringBean JwtService mockJwtService = Mock()`
fields, their own copy of the "stub `getClaim` to throw, conditionally attach a cookie, assert
`401`" mechanics (five times over in `AccountControllerSpec` alone — one per CRUD verb — plus once
in `AuthControllerSpec`), and an identical three-row `where:` table (`"no cookie"` /
`"malformed cookie"` / `"expired cookie"`) repeated at every one of those six call sites. The
malformed/expired split is also redundant at this specific layer: `JwtService` is fully mocked
here, so both rows stub `mockJwtService.getClaim(_ as String) >> { throw new
JwtParserException(exceptionMessage) }` identically, and `exceptionMessage` is never asserted on —
the two rows exercise the same mocked behaviour under different cosmetic labels. The real
distinction between a malformed token, an expired token, and one with an invalid signature is
already covered properly, against a *real* `JwtService`, by `JwtAuthenticationFilterSpec`
(`AUTH-001-AC-22`). This spec moves the shared Spring test doubles and the shared
"reject-unauthenticated" mechanics up into the existing `BaseControllerSpec` (added during the
`BACKEND-002`/`BACKEND-003` work to hold `AuthenticatedUser`/`createAuthenticatedUser`, which this
spec extends rather than duplicates), trims each affected `where:` table to the two cases this
layer can actually tell apart, and gets the resulting shared helper in place before
`TransactionControllerSpec` reaches its own `BACKEND-003-AC-27`.

The same duplication exists on the authenticated happy-path side: `new
Cookie(COOKIE_ACCESS_TOKEN, user.jwt)` is constructed inline 25 times in `AccountControllerSpec`
and once more in `AuthControllerSpec`'s `AUTH-001-AC-14`. No new helper is needed for this one —
`CookieHelper.accessTokenCookie` already exists (introduced alongside the rejection helper above)
and returns exactly what `.cookie(...)` needs, so this spec just switches every inline-constructed
call site over to it.

No production code changes — this is entirely `backend/src/test/groovy/...`.

## Requirements

### Requirement 1: Shared Spring test doubles live in one place

**User story**: As the person maintaining these specs, I want `MockMvc` and the mocked
`JwtService` declared once, so every `@WebMvcTest` controller spec inherits the same wiring
instead of re-declaring it.

#### BACKEND-004-AC-01 [AUTO]: BaseControllerSpec owns the shared MockMvc and mocked JwtService
**Statement**: The `BaseControllerSpec` shall declare an `@Autowired MockMvc mockMvc` field and a
`@SpringBean JwtService mockJwtService = Mock()` field, such that any subclass annotated
`@WebMvcTest` inherits both without redeclaring them.

**Rationale**: `AccountControllerSpec` and `AuthControllerSpec` currently declare an identical pair
of fields each. Spock's `@SpringBean` field injection is resolved over the whole class hierarchy,
so hoisting these causes no behaviour change — it only removes the duplicate declarations.

#### BACKEND-004-AC-02 [AUTO]: No subclass redeclares them
**Statement**: `AccountControllerSpec` and `AuthControllerSpec` shall not declare their own
`mockMvc` or `mockJwtService` fields, relying on `BaseControllerSpec`'s instead.

**Rationale**: A local redeclaration would shadow the inherited field silently rather than fail
loudly, so this is worth its own criterion rather than folding it into `AC-01`.

### Requirement 2: One shared method for "reject an unauthenticated request"

**User story**: As the person maintaining these specs, I want the cookie-stub-and-attach mechanics
written once, so each controller spec's own unauthenticated-request test supplies only what's
actually specific to its endpoint.

#### BACKEND-004-AC-03 [AUTO]: BaseControllerSpec exposes a shared rejection helper
**Statement**: The `BaseControllerSpec` shall expose a method that, given a
`MockHttpServletRequestBuilder` and a nullable cookie value, stubs `JwtService.getClaim` to throw
a `JwtParserException` when a cookie is supplied, conditionally attaches that cookie to the
builder, performs the request via the inherited `mockMvc`, and returns the resulting
`ResultActions`.

**Rationale**: This is the one piece that's byte-for-byte identical across all six existing call
sites (five in `AccountControllerSpec`, one in `AuthControllerSpec`) — the part that varies per
call site (the request builder itself, and the `0 * mock<X>Service...` verification) stays local
to each test.

#### BACKEND-004-AC-04 [AUTO]: Every existing rejection test calls the shared helper
**Statement**: Every existing "rejects an unauthenticated request" test in `AccountControllerSpec`
(create, list, view, update, delete) and `AuthControllerSpec` (`AUTH-001-AC-15`) shall call
`BaseControllerSpec`'s shared helper from `BACKEND-004-AC-03`, rather than repeating the
stub-and-attach logic inline, while still asserting its own endpoint-specific
`0 * mock<X>Service...` interaction locally.

**Rationale**: Consolidates six near-identical blocks into six short ones that differ only in what
they're actually testing. Doesn't change what any of them verify — `AUTH-001-AC-15`'s and
`BACKEND-002-AC-23`'s AC statements in their own specs are unchanged by this; only the test code
proving them is restructured.

### Requirement 3: Trim each `where:` table to what this layer can distinguish

**User story**: As the person maintaining these specs, I don't want to keep two rows that stub
identical mock behaviour under different labels, when the real distinction already has dedicated,
more faithful coverage elsewhere.

#### BACKEND-004-AC-05 [AUTO]: Two rows, not three
**Statement**: Every `where:` table covered by `BACKEND-004-AC-04` shall provide exactly two rows
— `"no cookie"` and `"invalid cookie"` — removing the separate `"malformed cookie"` and
`"expired cookie"` rows.

**Rationale**: At this mocked-`JwtService` layer, `"malformed cookie"` and `"expired cookie"` stub
the identical `mockJwtService.getClaim(_ as String) >> { throw new
JwtParserException(exceptionMessage) }` interaction, and `exceptionMessage` is never asserted on —
so the two rows currently prove nothing different from each other. The real malformed/expired/
invalid-signature distinction is already exercised properly, against a *real* `JwtService`, by
`JwtAuthenticationFilterSpec`'s `AUTH-001-AC-22`. This AC doesn't weaken `AUTH-001-AC-15`'s
statement (which still correctly describes the controller's behaviour for every one of those input
conditions) — it changes how that statement is demonstrated, shifting the burden of distinguishing
failure *modes* onto the spec that can actually tell them apart.

#### BACKEND-004-AC-06 [AUTO]: Consistent @Unroll
**Statement**: Every parameterized test covered by `BACKEND-004-AC-04` shall carry `@Unroll`, for
consistent per-scenario reporting.

**Rationale**: The five `BACKEND-002-AC-23` tests already have it; `AUTH-001-AC-15` currently
doesn't. Closes that gap as part of the same pass.

### Requirement 4: Available before TransactionControllerSpec needs it

**User story**: As the person about to write `TransactionControllerSpec`, I want the shared
pattern to already exist so `BACKEND-003-AC-27` is written against it from the start, not as yet
another inline copy to consolidate later.

#### BACKEND-004-AC-07 [MANUAL]: TransactionControllerSpec's AC-27 reuses the shared helper
**Statement**: When `TransactionControllerSpec`'s `BACKEND-003-AC-27` test is implemented, it
shall call `BaseControllerSpec`'s shared helper (`BACKEND-004-AC-03`) and use the same two-row
`where:` table (`BACKEND-004-AC-05`), rather than reintroducing the inline pattern this spec
removes elsewhere.

**Rationale**: `TransactionControllerSpec` doesn't exist yet beyond its label-only skeleton, so
there's nothing to automate against today. **Verified manually for now**: code review at the time
`BACKEND-003-AC-27` is actually written, confirming it reuses rather than re-derives the pattern.
**Route to automating later**: once written, this folds into `BACKEND-004-AC-04`'s automated
coverage and this AC can be considered superseded.

### Requirement 5: Authenticated requests build their cookie via the existing CookieHelper, not inline

**User story**: As the person maintaining these specs, I don't want `new
Cookie(COOKIE_ACCESS_TOKEN, user.jwt)` constructed by hand at every authenticated call site, when
an identical ready-made helper already exists for exactly this purpose.

#### BACKEND-004-AC-08 [AUTO]: Every existing authenticated call site reuses CookieHelper.accessTokenCookie
**Statement**: Every existing test in `AccountControllerSpec` and `AuthControllerSpec` that
currently constructs `new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)` inline shall instead call
`CookieHelper.accessTokenCookie(user.jwt)` inside the same `.cookie(...)` expression.

**Rationale**: `new Cookie(COOKIE_ACCESS_TOKEN, user.jwt)` is repeated verbatim 25 times across
`AccountControllerSpec`'s authenticated tests and once more in `AuthControllerSpec`'s
`AUTH-001-AC-14` — the same class of duplication `BACKEND-004-AC-03` already removed on the
unauthenticated-rejection side, just on the happy-path side instead. No new helper is needed:
`CookieHelper.accessTokenCookie` was already introduced for `AC-03`'s rejection helper and returns
a `Cookie[]` that `.cookie(Cookie...)` accepts exactly as it would a single `Cookie`, so this is a
one-line swap inside the existing `.cookie(...)` call at each site — not a restructuring of the
surrounding builder chain (several of these call sites still chain `.contentType(...)`/
`.content(body)` after the cookie, and must keep doing so).

#### BACKEND-004-AC-09 [MANUAL]: TransactionControllerSpec's future authenticated tests reuse it too
**Statement**: When `TransactionControllerSpec`'s authenticated test bodies (e.g.
`BACKEND-003-AC-06`) are implemented, they shall build their cookie via
`CookieHelper.accessTokenCookie`, matching `BACKEND-004-AC-08`, rather than constructing a `Cookie`
inline.

**Rationale**: Mirrors `AC-07`'s reasoning — `TransactionControllerSpec` currently only has
label-only skeletons, so there's nothing to automate against today. **Verified manually for now**:
code review at the time those tests are actually written, confirming they reuse rather than
re-derive the pattern. **Route to automating later**: once written, this folds into `AC-08`'s
automated coverage and this AC can be considered superseded.

## Cross-references

| Contract | Defined here | Used by |
|---|---|---|
| `AUTH-001-AC-15` (`AuthControllerSpec`'s missing/invalid-session test) | `backend_spec_001_basic_login.md` | This spec (`AC-04`/`AC-05`/`AC-06` restructure its test code; its AC statement is unchanged) |
| `AUTH-001-AC-20`–`AC-22` (`JwtAuthenticationFilterSpec`, real `JwtService`, real malformed/expired/invalid-signature coverage) | `backend_spec_001_basic_login.md` | This spec (`AC-05`'s justification for dropping that distinction at the mocked layer) |
| `AUTH-001-AC-14` (`AuthControllerSpec`'s valid-session test) | `backend_spec_001_basic_login.md` | This spec (`AC-08` restructures its cookie construction; its AC statement is unchanged) |
| `BACKEND-002-AC-23` ×5 (`AccountControllerSpec` create/list/view/update/delete) | `backend_spec_002_accounts.md` | This spec (`AC-04`/`AC-05`/`AC-06` restructure these five tests; AC statement unchanged) |
| `BACKEND-002-AC-07`–`AC-22`, `AC-24a`–`AC-24f` (`AccountControllerSpec`'s authenticated happy-path and validation tests) | `backend_spec_002_accounts.md` | This spec (`AC-08` restructures their cookie construction; AC statements unchanged) |
| `BACKEND-003-AC-27` (`TransactionControllerSpec`, not yet implemented) | `backend_spec_003_transactions.md` | This spec (`AC-07` — written against the shared infra from the start) |
| `BACKEND-003-AC-06` and other authenticated `TransactionControllerSpec` ACs (not yet implemented) | `backend_spec_003_transactions.md` | This spec (`AC-09` — written against `CookieHelper.accessTokenCookie` from the start) |
| `BaseControllerSpec` (`AuthenticatedUser`, `createAuthenticatedUser`, `createAuthenticatedAdminUser`, `createAuthenticatedRole`) | Added ad hoc during `BACKEND-002`/`BACKEND-003` work (no dedicated spec) | This spec extends the same class rather than introducing a second helper location |
| `CookieHelper.accessTokenCookie` (returns a `Cookie[]` for `.cookie(...)`) | Added ad hoc during `BACKEND-002`/`BACKEND-003` work (no dedicated spec) | This spec's `AC-03` (unauthenticated path) and `AC-08` (authenticated path) both reuse it rather than constructing `Cookie` inline |

## Acceptance Criteria Summary

- [x] BACKEND-004-AC-01 [AUTO]: `BaseControllerSpec` declares the shared `mockMvc`/`mockJwtService` fields
- [x] BACKEND-004-AC-02 [AUTO]: No subclass redeclares them
- [x] BACKEND-004-AC-03 [AUTO]: `BaseControllerSpec` exposes a shared "reject unauthenticated request" helper
- [x] BACKEND-004-AC-04 [AUTO]: Every existing rejection test (5× `BACKEND-002-AC-23` + `AUTH-001-AC-15`) calls it
- [x] BACKEND-004-AC-05 [AUTO]: Every such `where:` table has exactly two rows ("no cookie" / "invalid cookie")
- [x] BACKEND-004-AC-06 [AUTO]: Every such parameterized test carries `@Unroll`
- [ ] BACKEND-004-AC-07 [MANUAL]: `TransactionControllerSpec`'s future `BACKEND-003-AC-27` reuses the shared helper and two-row table — `AC-27` itself is still a label-only skeleton; nothing to verify yet
- [x] BACKEND-004-AC-08 [AUTO]: Every existing authenticated call site (25× in `AccountControllerSpec` + `AUTH-001-AC-14`) reuses `CookieHelper.accessTokenCookie` instead of constructing `Cookie` inline
- [ ] BACKEND-004-AC-09 [MANUAL]: `TransactionControllerSpec`'s future authenticated tests reuse `CookieHelper.accessTokenCookie` too — true for every one implemented so far (`AC-06`–`AC-11b`); list/view/update/delete still to come, re-check as each lands

## Test case sketches

Per the Working Agreement, these are scenario + block-structure sketches only — `backend-dev`
turns each into a label-only Spock skeleton; the human fills in real `given`/`when`/`then` content.

```groovy
def "BACKEND-004-AC-01/AC-02: BaseControllerSpec's shared mockMvc/mockJwtService are inherited, not redeclared"() {
    given: "AccountControllerSpec and AuthControllerSpec, neither declaring its own mockMvc or mockJwtService field"

    when: "a WebMvcTest context is built for either spec"

    then: "the inherited fields from BaseControllerSpec are injected and usable"
}
```

```groovy
def "BACKEND-004-AC-03: the shared helper stubs, attaches, and performs for a given cookie"() {
    given: "a request builder and a cookie value"

    when: "the shared helper is called with them"

    then: "getClaim is stubbed to throw only when a cookie is present, the cookie is attached when present, and the request is performed"
}
```

```groovy
def "BACKEND-004-AC-04: BACKEND-002-AC-23 (create) calls the shared helper"() {
    given: "an authenticated-less request to POST /api/accounts"

    when: "the shared helper performs the request"

    then: "the response is 401 Unauthorized"

    and: "AccountController's createAccount is never invoked"
}
```

```groovy
def "BACKEND-004-AC-04: AUTH-001-AC-15 calls the shared helper"() {
    given: "a request to GET /api/auth/me with a missing or invalid-cookie scenario"

    when: "the shared helper performs the request"

    then: "the response is 401 Unauthorized"
}
```

```groovy
@Unroll
def "BACKEND-004-AC-05/AC-06: #scenario is rejected, unrolled over exactly two rows"() {
    given: "either no cookie, or a cookie that fails JwtService parsing for any reason"

    when: "the shared helper performs the request"

    then: "the response is 401 Unauthorized"

    where: "exactly two rows: no cookie, invalid cookie"
}
```

```groovy
def "BACKEND-004-AC-07: TransactionControllerSpec's future AC-27 reuses the shared helper [MANUAL]"() {
    given: "a request to a /api/accounts/{accountId}/transactions endpoint with no valid access-token cookie"

    when: "the request is made via BaseControllerSpec's shared helper"

    then: "the response is 401 Unauthorized"

    // MANUAL for now: verified by code review when BACKEND-003-AC-27 is actually implemented,
    // confirming it reuses this spec's shared helper and two-row where: table rather than
    // reintroducing the inline pattern this spec removes elsewhere.
}
```

```groovy
def "BACKEND-004-AC-08: BACKEND-002-AC-07 (create) builds its cookie via CookieHelper.accessTokenCookie"() {
    given: "an authenticated user and a valid account creation request"

    when: "POST /api/accounts is requested with the cookie built via CookieHelper.accessTokenCookie"

    then: "the response is 201 Created with the created account"

    and: "the created account is owned by the authenticated user"
}
```

```groovy
def "BACKEND-004-AC-08: AUTH-001-AC-14 builds its cookie via CookieHelper.accessTokenCookie"() {
    given: "a valid, unexpired access-token cookie for an existing user, built via CookieHelper.accessTokenCookie"

    when: "GET /api/auth/me is requested with that cookie"

    then: "the response is 200 OK with the user's id, email, displayName, and role"
}
```

```groovy
def "BACKEND-004-AC-09: TransactionControllerSpec's future authenticated tests reuse CookieHelper.accessTokenCookie [MANUAL]"() {
    given: "an authenticated user and a valid transaction creation request, with the cookie built via CookieHelper.accessTokenCookie"

    when: "POST /api/accounts/{accountId}/transactions is requested"

    then: "the response is 201 Created with the created transaction"

    // MANUAL for now: verified by code review when TransactionControllerSpec's authenticated
    // tests are actually implemented, confirming they reuse CookieHelper.accessTokenCookie rather
    // than reintroducing the inline Cookie construction this spec removes elsewhere.
}
```

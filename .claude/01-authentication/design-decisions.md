# Authentication — Design Decisions

Resolves `.claude/00-initial-setup/02-open-questions.md` Q3's "needs its own design pass" status.
Written 2026-10-08, before `spec-writer` drafts the backend/frontend auth specs — intended as the
source `spec-writer` should read for these decisions rather than re-deriving or re-asking them.
Deferred/out-of-scope items raised during this pass live in `spec_candidates.md`, not here.

## Access token

- Lifetime: **15 minutes**.
- Signing: **HS256** (HMAC-SHA256) — this app is both issuer and verifier, no reason for
  asymmetric RS256.
- Claims: **user id (`sub`) + role only.** No email/PII in the token. Role is safe to bake in at a
  15-minute lifetime — a stale "is admin" claim for up to 15 min after a demotion is an accepted
  tradeoff.

## Refresh token

- Lifetime: **7 days, sliding** (each successful refresh extends it).
- **Server-side state required**: a DB-backed table storing a *hash* of the token (never the raw
  value), user id, expiry, and a `revoked`/`used` flag. A JWT alone can't be revoked early.
- **Rotation**: a new refresh token is issued on every use; the old one is immediately marked
  `used`. A refresh token is never redeemable twice.
- **Reuse detection**: presenting an already-`used`/revoked refresh token is treated as a
  stolen/replayed token — revoke *every* outstanding refresh token for that user, forcing a full
  re-login on all sessions. Standard mitigation for refresh-token theft; the whole reason rotation
  exists.

## Transport & CSRF

- Both tokens as **httpOnly, Secure, SameSite=Strict cookies**. The refresh cookie's `Path` is
  scoped to `/api/auth/refresh` only, so it isn't sent on normal API calls — only the access
  cookie travels with those.
- **CSRF: SameSite=Strict alone, no separate CSRF token.** With `SameSite=Strict` and the
  frontend/backend staying same-origin (Vite already proxies `/api` through the same origin in
  dev — keep that true in production too), the browser never sends the cookie cross-site, which
  blocks the classic CSRF vector. Revisit only if cross-origin use is ever genuinely needed.

## Password hashing

- **BCrypt** via Spring Security's `BCryptPasswordEncoder`, default cost factor. Hand-coded by the
  human per the Working Agreement — recorded here only so the spec can reference it as the agreed
  approach.

## Registration / login identifier

- **Email as the login identifier** — no separate username. Unique constraint, **case-insensitive**
  (`Foo@x.com` and `foo@x.com` collide).
- **Password policy: minimum length 12 characters, no mandated character-class complexity.**
  Favors current NIST guidance (length over forced complexity) and is simpler to implement/test.
- **No email verification in v1** — see `spec_candidates.md` for the full deferral rationale and
  what it would take to add later.

## Registration auto-login

- **Registration issues the same access-token cookie login does** — a newly registered user is
  immediately signed in, not required to make a separate login call. Decided 2026-10-08, revising
  `backend_spec_001_basic_login`'s original (stricter) draft, which had registration create the
  account only.
- **Why**: checked `modern-web-guidance` first — it has no guidance on this (only autofill-attribute
  and passkey-flow guides exist, nothing on post-registration session flow). General UX consensus
  (web search) is that auto-login after registration is standard practice, reducing friction for a
  user who just typed their password once already; the usual objection is shared-device account
  switching, which doesn't apply here. OWASP's standard counter-concern, session fixation, doesn't
  apply to this design either: fixation requires a pre-existing session/cookie value (set before
  auth) to be reused after login, and there is no pre-auth cookie in this flow — the JWT is signed
  fresh server-side at issuance regardless of which endpoint triggers it.

## Display name

- **A single required `displayName` field, free text**, captured at registration (not first/last
  name — no current need to address users formally or sort by surname).
- **Not included in JWT claims** — keeps the access token minimal (id + role only, per above).
  Instead, returned in the login/register response body, and via a session-rehydration endpoint
  (see below).
- **`GET /api/auth/me`**: since the access token lives in an httpOnly cookie, the frontend can
  never read it directly — this endpoint validates the cookie server-side and returns
  `{ id, email, displayName, role }` so the app can answer "is anyone logged in, and who" on load
  (e.g. after a page refresh), not just immediately after login/register. This falls out of the
  httpOnly-cookie decision rather than being a separate feature choice.

## Admin bootstrap

- **Manual, one-time DB update.** Register normally through the app as a USER, then hand-run one
  SQL UPDATE to flip that account's role to ADMIN. No bootstrap code, config property, or seed
  migration exists for this — least machinery for a single real admin account.

## Admin scope (v1)

Admin can:
- **List/view all user accounts.**
- **Force-reset another user's password** — the recovery path for a lost password given there's
  no email-based reset flow in v1. Side effect: a force-reset **revokes all of that user's existing
  refresh tokens** (same logic the reuse-detection path already needs — changing credentials should
  kill existing sessions).

Admin cannot (v1): delete another user's account, edit another user's details, or promote/demote
roles. These were discussed and deliberately deferred, not forgotten — see `spec_candidates.md`
for why and what each would need.

**Standing invariants**, v1 and beyond: an admin can never delete or demote their own account, and
the system must always retain at least one ADMIN.

## Logout

- **Single-session only** — logout revokes just the current refresh token. Other active
  sessions/devices are unaffected. Matches typical user expectation of what "logout" means.
  "Logout everywhere" was considered and intentionally left for later (see `spec_candidates.md`).

## Spec breakdown

Decided 2026-10-08: this feature is **6 specs, not 1 or 2** — a backend + frontend pair for each
of 3 stages, matching the build order's own "simplest secure version, then add complexity once
understood" staging rather than one monolithic auth spec:

1. **`basic_login`** — `User` entity (incl. required `displayName`), register, login, a single
   access-token cookie only (no refresh yet), `GET /api/auth/me`, logout. The "one request through
   the whole stack" first pass.
2. **`refresh_tokens`** — the refresh-token entity, rotation on every use, reuse detection →
   revoke-all-for-user.
3. **`admin_account_management`** — list/view users, force-reset password, the two standing
   invariants (can't self-delete/demote, always ≥1 admin).

Each stage's frontend spec `Depends on` that stage's backend spec; stages 2 and 3 each `Depend on`
stage 1. Invoke `spec-writer` once per spec (6 invocations total), in this order, pointing it at
this file for the relevant stage's decisions each time.

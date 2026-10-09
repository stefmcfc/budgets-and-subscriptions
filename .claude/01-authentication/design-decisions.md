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

## Email storage

- **Stored as plain text in the `email` column** — not hashed, not application-level encrypted.
  Decided 2026-10-08.
- **Why plain text is actually correct here, not just simplest**: email needs to support three
  things a one-way hash (right choice for the password) would break entirely — a case-insensitive
  *lookup* at login (`WHERE lower(email) = ?`, not just an equality check against one known value),
  *display* back to the user (`UserResponse.email` in every register/login/me response), and, if
  email verification is ever picked up later (`spec_candidates.md`), actually *sending* to it.
  Reversible application-level encryption (e.g. a JPA `AttributeConverter` doing AES) would
  preserve recoverability, but at a real cost: it typically breaks native case-insensitive
  uniqueness/indexing unless done deterministically (which weakens the whole point of encrypting
  it), and adds key-management machinery (rotation, where the key itself lives) for a column most
  production systems don't encrypt at the app layer in the first place.
- **Implementation note for the migration** (yours to decide/hand-code, not a design decision
  being made here): the case-insensitive uniqueness constraint (`AUTH-001-AC-01`) needs a DB-level
  mechanism — a functional unique index on `LOWER(email)`, or Postgres's `citext` column type.

### PII implications, and what this would look like in a real enterprise project

Email is personal data (PII) under GDPR/UK-GDPR and most privacy regimes, even stored in plain
text — "PII" describes *what the data is*, not how it's encoded at rest. Plain-text storage here
is a reasonable call for this project's actual threat model: local-only hosting, single real user,
no cloud deployment, no third parties with DB access. That calculation changes completely in a
real multi-tenant enterprise system, where I'd expect most or all of the following, roughly in
order of how cheap they are to adopt:

- **Infrastructure-level encryption at rest** (disk/volume encryption, encrypted DB backups, TLS
  everywhere in transit) as the baseline — this is the actual industry-standard control for email,
  not column-level crypto. Nearly free to turn on with most managed Postgres offerings (RDS, Cloud
  SQL) and should exist regardless of what's encrypted at the application layer.
- **Strict access control and audit logging** on who/what can query the `users` table at all —
  least-privilege DB roles (the app's own connection shouldn't be able to do things an admin tool
  needs), and an audit trail of admin actions against user records (relevant here too, since
  `admin_account_management` already lets an admin touch other users' accounts).
- **Never logging PII** — application logs, error traces, and APM/observability tooling must not
  capture raw emails (or anything else PII) even incidentally; this is a common real-world leak
  vector that has nothing to do with how the DB column itself is encoded.
- **Data minimization and retention policy** — collect only what's needed (this project already
  does: email + a display name, nothing more), and have an explicit retention/deletion policy
  rather than keeping every record indefinitely by default.
- **A real deletion/right-to-erasure path** — GDPR's "right to be forgotten" means an account
  deletion feature (already deferred here, see "Extended admin account management" in
  `spec_candidates.md`) needs to actually erase or irreversibly anonymize PII, not just flip a
  `deleted` flag while the row (and email) lingers.
- **Field-level encryption for genuinely high-sensitivity fields only** — reserved for things like
  bank account numbers, national ID numbers, or payment data (this project's own account-balance
  data arguably qualifies more than email does), using envelope encryption via a managed KMS/HSM
  with key rotation — not applied blanket-wide to every PII column, since it has real query/index
  and operational costs that aren't worth paying everywhere.
- **Formal classification and process**: a data classification policy (what's PII vs. sensitive vs.
  public), a DPIA (Data Protection Impact Assessment) for anything handling PII at scale, a named
  data controller/processor relationship if a third party is involved, and documented breach
  notification procedures (GDPR's 72-hour rule) — all organizational/process controls that exist
  independently of any code, and that a one-user local app has no practical analog for.

The honest summary: encrypting the `email` column itself would be a relatively low-value, highly
visible thing to point at and say "secure," while the controls that actually matter at enterprise
scale (access control, audit logging, not leaking PII into logs, retention/deletion policy,
infrastructure-level encryption) are mostly invisible and have nothing to do with the column's own
encoding. Good security engineering optimizes for the first list, not for encrypting fields that
don't need it.

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

# Spec Candidates — Authentication

Ideas and decisions raised during auth design that were deliberately deferred past v1, rather than
built now or forgotten. Each entry should have enough detail that picking it up later doesn't
require re-deriving the research. Append rather than overwrite, same convention as
`.claude/00-initial-setup/02-open-questions.md`.

## Email verification on registration

**Status: Deferred past v1.** Not part of the backend or frontend auth spec. `User` ships with no
verification-related columns — add them only when this is actually picked up (a clean additive
Flyway migration at that point, not a retrofit).

**Why deferred:** registration is single-user/invite-only in practice (not open signup), so
verifying real ownership of an email address has little value yet. It also doubles the state
machine for v1's auth flow (register → verify → *then* allow login, vs. just register → login),
which cuts against the build order's "simplest secure version first" goal.

**What it would need, when picked up:**
- New dependency: `spring-boot-starter-mail`
- A verification-token concept — either a `verified` boolean + `verification_token` + expiry
  columns on `User`, or a small separate `VerificationToken` entity — either way, a new Flyway
  migration
- `GET /api/auth/verify?token=...` endpoint, plus a "resend verification" endpoint for
  expired/lost tokens
- A decision on login behavior for unverified accounts: block entirely vs. allow with a nag banner
- Spock tests need either a mocked `JavaMailSender` or an embedded test SMTP server (e.g.
  GreenMail) to verify sending without hitting a real inbox

**Free sending options surveyed** (all workable at single-user volume):
- **Gmail SMTP via an App Password** — genuinely free, zero new signups if already using Gmail,
  just `spring.mail.*` config + an app password (needs 2FA enabled on the Google account). Not
  meant for production bulk mail, irrelevant at this scale. Simplest option if/when revisited.
- **Brevo** (formerly Sendinblue) — free tier, 300 emails/day, a proper transactional API if
  practice with that pattern (vs. raw SMTP) is wanted instead.
- **Resend** — free tier, but sandbox mode only delivers to the account's own verified email until
  a domain is verified — fine for a single real user, not for a second one later.

## Extended admin account management

**Status: Deferred past v1.** `product.md`/the Q3 open-question originally described admin as able
to "view/edit/delete other users' accounts." The v1 auth design pass narrowed this to just
**list/view all users** and **force-reset another user's password** — the two capabilities needed
to recover from a lost password without an email-based reset flow. The following were considered
and intentionally left out of v1, not forgotten:

- **Delete another user's account** — needs a cascade-vs-soft-delete decision once the data model
  (accounts/transactions/etc, all owned by a user) actually exists; premature to design against an
  empty schema.
- **Edit another user's details** (e.g. email) — raises whether/how the affected user is notified
  of a change made on their behalf; no notification mechanism exists yet (see in-app-only reminders
  decision in `02-open-questions.md` Q9 — same gap).
- **Promote/demote USER↔ADMIN role** — would need the "always ≥ 1 admin" invariant either way;
  deferred because the only admin-creation path in v1 is a manual one-time DB update (see
  `02-open-questions.md` Q3 follow-up), so there's no in-app role management to extend yet.

**Invariants that still apply to whatever admin scope exists**, v1 or later: an admin can never
delete or demote their own account, and the system must always retain at least one ADMIN.

# Outstanding Questions

Each question gets answered, and may pick up a `> **Follow-up**` / `> **Answer**` / `> **Status**` block underneath as it's discussed further — this file is expected to go through several rounds before requirements settle, so append rather than overwrite.

## 1) Data entry: manual entry, CSV import, or bank integration (e.g. Open Banking)? This shapes the whole design.

Initially manual entry. CSV import and data integration can be considered in the future

## 2) Database: which one, and how to manage schema changes (Flyway/Liquibase or none)?

PostgreSQL has been added to the Spring Boot project. Is anything else required

> **Follow-up (Claude):** yes — two things weren't pinned down yet: (a) migration tooling (Flyway vs. Liquibase vs. hand-rolled DDL), and (b) a local Postgres instance to develop against.
>
> **Answer:** Flyway. Local Postgres via Docker.
>
> **Status: Resolved.** (Exact docker-compose/image setup is an implementation detail for whoever hand-codes it, not a design decision.)

## 3) Auth approach: session-based or token-based (JWT)? Single user or multi-user?

Token-based as this will give me experience using them. 

It will probably be just me using it, but again, I want experience with auth, so maybe we design it multi-user so
that we can at least have a manual test account, automated test and a real account

> **Follow-up (Claude):** "token-based" + multi-user leaves the risky details unanswered, and these are exactly the security decisions the project's working agreement reserves for hand-coding rather than Claude authoring — worth pinning down before implementation starts rather than discovering mid-build:
> - Where does the JWT live client-side — httpOnly cookie, or `localStorage`/memory?
> - Refresh tokens, or short-lived access token only with re-login?
> - Since it's multi-user but not open signup — how do accounts get created?
>
> **Answer:** httpOnly cookie (per Claude's recommendation — safer against XSS than `localStorage`). Refresh tokens — yes, want the experience. A registration page is needed. Also want an admin account/role that can delete or alter other accounts' details, with admin-only pages and functionality — explicitly called out as "a good thing to learn."
>
> **Status: Open.** This has grown beyond a single auth decision into a small feature set of its own: registration flow, access + refresh token lifecycle, and a `USER`/`ADMIN` role model with admin-only account management screens. Needs its own design pass (token expiry windows, refresh rotation/reuse detection, what admin can/can't do to another account) before the first line of auth code is hand-written — but the overall shape is decided.
>
> **Status: Resolved (2026-10-08).** Design pass complete — full detail in `.claude/01-authentication/design-decisions.md`. Summary: access token 15 min/HS256/claims=id+role only; refresh token 7-day sliding, DB-backed hashed, rotated every use, reuse detected → revoke all of that user's tokens; both delivered as httpOnly/Secure/SameSite=Strict cookies (refresh cookie path-scoped to `/api/auth/refresh`); CSRF relies on SameSite=Strict alone, no separate token; login identifier is email (case-insensitive unique), password policy is min-length-12 with no forced complexity; first admin is created via a manual one-time DB update, no bootstrap code; admin scope for v1 is list/view users + force-reset another user's password only (not delete/edit/role-promotion — those are deferred, see `.claude/01-authentication/spec_candidates.md`); standing invariants are admin can't delete/demote self and the system always keeps ≥1 admin; logout is single-session only (revokes just the current refresh token).

## 4) Front end: what will the client be (server-rendered, SPA, or API-only to start)?

Already answered (React)

## 5) Categories: fixed list or user-defined? Can a transaction have more than one?

User defined, maybe with some presets. Yes, allow a transaction to have more than one

## 6) Subscription and game detection: tag manually, or detect recurring/merchant patterns automatically?

Both, merchant patterns should get the majority

> **Reopened:** the original framing was specific to subscriptions and game microtransactions, but merchant/recurring-pattern detection is a general mechanism — it doesn't care what the merchant *is*. Proposal: build one general "recurring/merchant-pattern detection" feature, and let subscriptions and game microtransactions both become *instances* that implement/use it (i.e. they're detected and tagged by the same underlying engine, not two separate detectors). Also connects to Q7's "flag recurring vs one-off" — likely the same mechanism.
>
> **Status: Open.** Shape agreed in principle (generalize the detection mechanism); needs the same design pass Q8 is getting — see note there about re-examining Core Features for things that were written too specifically.

## 7) Big purchase threshold: fixed amount, or relative to typical spending?

Fixed should be fine. We probably also want a way to flag recurring payments and one-off

> **Follow-up (Claude):** Q5 (category auto-suggestion), Q6 (recurring/subscription detection), and this big-purchase/recurring flag are all the same shape of problem — "does this transaction's merchant/amount/frequency match a known pattern?" Proposed unifying them into one transaction-classification engine with three consumers, rather than three separate ad-hoc mechanisms, since it affects the data model (a shared `MerchantPattern`/`Rule` concept) rather than being a pure wording change.
>
> **Answer:** Agreed.
>
> **Status: Open — design pass needed.** One classification engine backing category suggestion, recurring/subscription detection, and big-purchase flagging. Still needs: what a "rule" actually matches on (merchant name, amount range, frequency), and how manual tagging interacts with/overrides an automatic match.

## 8) Brother's balance: tag transactions manually as "brother's purchase"? How are repayments recorded and matched?

We probably want a way to be able to create transactions for more than one person. Brother / friend / parent, so would 
be a tag, but also assigned to a person. This should also allow starting balances, cash transactions

> **Follow-up (Claude):** this reads as a scope change, not just an answer — generalizing a single "brother's Amazon balance" feature into a general multi-person ledger (arbitrary people, starting balances, cash transactions not tied to a bank account). Confirmed as intentional?
>
> **Answer:** Yes, intentional scope change. The main point of the project is learning how a bigger app works in practice, so Core Features should be updated to match — not just this one answer.
>
> **Status: Open — Core Features rewrite needed.** Becomes a general "multi-person running balances" feature (person has a name, a starting balance, and a running total from both card and cash transactions assigned to them), with "brother's Amazon balance" as the first real use case rather than the feature itself. This is also the prompt to re-examine the rest of Core Features for anything else written too narrowly around the original specific examples — Q6 above is the first other candidate found; more may turn up as this list is revisited.

## 9) Reminders: in-app only, email, or push? How are bank-switch offers sourced?

In-app for now, others could be future ideas. 

I think user would look manually for now, we can list sites that do comparisons though, not sure if there any APIs out
there that di this?

> **Follow-up (Claude):** this was framed narrowly as bank-switching reminders specifically. Is the general capability — "remind me about X on date Y" — reusable for other things (subscription/insurance renewals etc.), or is it bank-switching only for now?
>
> **Answer:** General. Example given: sign up for something with a fixed term (annual holiday insurance, a subscription), set a reminder to get a notification some number of days before renewal to review it. Bank-switching (1b below) is the first concrete use case, not the whole feature.
>
> **Status: Open — Core Features rewrite needed.** "Reminders" becomes a general date-triggered notification capability; bank-switching and subscription-renewal review are both instances of it.

## 10) Money handling: currency (GBP only?) and use of BigDecimal for amounts

Yes, single currency please. It could be that we flag foreign transactions with their currency, but that would be 
informational only since the transaction will have been done by the bank or merchant anyway

## 11) Hosting/deployment: local only for now, or deployed somewhere?

Yes local only for now

## 12) Testing approach: what levels of testing, and what tooling?

Back-end unit testing will be done with the Spock framework using TDD. Eventually we would want black box, integration 
testing, maybe mock servers

---

# Core Features Review

Prompted by Q8's scope change — re-scanning `CLAUDE.md`'s Core Features list for anything else written too narrowly around one specific original example rather than the general capability.

## 1a) "Accounts and transactions — money in/out across bank accounts..."

> **Flagged (Claude):** "bank accounts" is narrower than what Q8's multi-person ledger already needs (cash transactions). Is personal account tracking bank-only, or broader?
>
> **Answer:** Broader — there are two things being tracked. Traditional bank accounts (e.g. "Current Account with Bank A", "Savings with Bank B") and their transactions. Other account types to consider: Cash ISA, Stocks & Shares ISA, pensions, investment accounts (e.g. Vanguard), credit cards.
>
> **Status: Open — Core Features rewrite needed.** "Accounts" generalizes to multiple account *types* (current, savings, ISA, investment/pension, credit card), not just "bank accounts." Investment/pension accounts likely need different semantics than a transaction ledger (valuation over time vs. discrete in/out transactions) — worth a dedicated design question once this build step is reached, not now.

## 1b) Bank-switching reminder (bundled into Feature 1, also separately in Build Order step 6)

> **Flagged (Claude):** bundling the reminder description into Feature 1's text duplicates/risks drifting from Build Order step 6's "Reminders (bank switching)." What's the actual intent?
>
> **Answer:** "I opened this account as it has a bonus interest rate for 1 year, I want a reminder on date X to look for other accounts." Confirmed as an instance of the general reminders capability (see Q9 above), not a bespoke bank-specific mechanism.
>
> **Status: Resolved** (folds into Q9's general reminders rewrite — no separate bank-specific design needed).

## 2) Spending insights ("...including subscriptions and game microtransactions...per-subscription/per-game breakdowns")

> **Flagged (Claude):** written around the two original examples rather than the general capability — confirmed generalization correct?
>
> **Answer:** Yes, correct.
>
> **Status: Open — Core Features rewrite needed.** Breakdowns by category/recurring-merchant generally, with subscriptions and game microtransactions as examples of data it produces, not the feature itself — consistent with Q6/Q7's classification-engine unification above.

## 3) "Brother's Amazon running balance"

Already covered under Q8 above — noted here only as cross-reference. **Status: Open — Core Features rewrite needed** (see Q8).

## Auth / admin — missing from Core Features entirely

> **Flagged (Claude):** the registration flow and admin role/account-management pages (from Q3's follow-up) exist only inside the auth design notes, not as a Core Feature line — despite being real UI/functionality a user will actually use.
>
> **Answer:** Confirmed, needs adding.
>
> **Status: Open — Core Features rewrite needed.** Add an explicit Core Feature (or sub-feature) for auth/registration + admin account management, rather than leaving it implicit inside the Q3 auth design notes.

## Idea: multi-person ledger → lightweight bill-splitting

> **Raised (Claude):** once arbitrary people and running balances exist (Q8), a natural extension is splitting a single transaction's cost between people (e.g. 60/40) rather than one person owing the full amount.
>
> **Answer:** Good future idea.
>
> **Status: Deferred — explicit non-goal for v1.** Worth a one-line mention in Core Features as a deliberately-deferred idea, to head off scope creep later rather than silently forgetting it was considered.

---

**Next step:** rewrite `CLAUDE.md`'s Core Features section to reflect every "Open — Core Features rewrite needed" item above (1a, 1b→resolved, 2, 3, Auth/admin, Reminders generalization, classification-engine unification, bill-splitting as a noted non-goal).
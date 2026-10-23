# Spec Candidates — Accounts and Transactions

Ideas and decisions raised during this stage's design pass that were deliberately deferred past
v1 of this stage, rather than built now or forgotten. Each entry should have enough detail that
picking it up later doesn't require re-deriving the research. Append rather than overwrite, same
convention as `.claude/00-initial-setup/02-open-questions.md` and
`.claude/01-authentication/spec_candidates.md`.

## Credit cards (full semantics)

**Status: Deferred past this stage.** Not part of `accounts`/`transactions`. `AccountType` ships
with no `CREDIT_CARD` value — add it only when this is actually picked up.

**Why deferred:** a credit card doesn't fit the plain ledger model (`openingBalance` + discrete
`Transaction`s) as cleanly as current/savings/Cash ISA do. During this design pass, "full
credit-card semantics" was explicitly chosen over a simplified version — credit limit, statement
balance vs. current balance, and a due date/minimum payment — which is real, additional scope
beyond what this stage's plain-ledger `Account` needs, not a trivial enum addition.

**What it would need, when picked up:**
- A `creditLimit` field on whatever the credit-card account shape ends up being (possibly a
  subtype/separate entity rather than a new `AccountType` enum value on the plain-ledger `Account`,
  given how different its semantics are)
- A distinction between **current balance** (running total of all posted transactions, same idea
  as a plain ledger account) and **statement balance** (the balance as of the last statement
  date — what's actually due) — these are genuinely different numbers on a real credit card
- A statement cycle (a recurring date each month) and a **due date** derived from it
- A minimum-payment concept, and whether/how a reminder (build order step 5 — general
  date-triggered reminders) hooks into "statement due in N days" the same way it will for
  subscription-renewal review
- A decision on whether interest/APR modeling belongs in scope at all, or stays purely manual
  entry (a user enters the interest charge as a transaction, same as any other) — leaning toward
  the latter (manual entry, no APR calculation) to avoid rebuilding a credit-card interest engine,
  but worth confirming explicitly when this spec is actually picked up

## Investment, pension, and Stocks & Shares ISA accounts

**Status: Deferred past this stage.** Not part of `accounts`/`transactions`. `AccountType` ships
with no `INVESTMENT`/`PENSION`/`STOCKS_AND_SHARES_ISA` values.

**Why deferred:** these don't behave like a transaction ledger at all — their natural shape is a
**valuation over time** (what is this account worth as of date X), not a stream of discrete
in/out transactions the way a current account's spending is. Forcing them into the plain-ledger
`Account`/`Transaction` model (the "treat everything as one transaction ledger" option considered
and rejected during this design pass) would mean modeling every dividend, contribution, and market
movement as a synthetic "transaction," which is both inaccurate to what's actually happening and a
poor fit for this stage's running-balance mechanism (a stored, transactionally-maintained balance
makes little sense for a value that moves with the market, not with discrete user-entered events).

**What it would need, when picked up:**
- A different account shape entirely — most likely a `Valuation` entity (account FK, date, value)
  rather than `Transaction`, with "current balance" being simply the most recent valuation, not a
  running sum
- A decision on whether contributions/withdrawals are tracked as their own events (useful for
  "how much have I actually put in vs. investment growth" insight) separately from the periodic
  valuation itself
- Pension accounts specifically may also want employer-contribution tracking as a distinct concept
  from personal contributions — a real pension-specific nuance to design for explicitly rather than
  assume away
- Whatever spending-insight features (build order step 3) eventually want to say about these
  accounts will look different from a current account's "categories/recurring merchants" framing —
  worth revisiting insight design once this account shape actually exists

## Foreign-currency informational flag

**Status: Deferred past this stage.** `Transaction` ships with no currency-related column.

**Why deferred:** `product.md`'s Known Constraints already hedges this as a "may be flagged
informationally" maybe, not a committed v1 feature, and `.claude/00-initial-setup/02-open-questions.md`
Q10's answer treats it the same way ("It could be that we flag foreign transactions... but that
would be informational only"). Building it now would be speculative scope against a feature that
was never actually committed to, for a project whose stated primary goal is learning the core
ledger/account mechanics first.

**What it would need, when picked up:**
- A nullable `foreignCurrencyCode` (ISO 4217, e.g. `USD`) and `foreignAmount` pair on `Transaction`,
  purely informational — `amount`/`balance` arithmetic stays entirely in GBP regardless, per the
  single-currency decision (Q10) that this doesn't reopen
- A decision on where the display-only foreign amount actually gets shown (transaction list? a
  tooltip/detail view only?) — a frontend design question, not just a backend column addition

## Sensitive account identifiers (account number, sort code; later card number/CVV)

**Status: Deferred past this stage** (raised during this design pass, not from an original open
question). Not part of `accounts`. No `accountNumber`/`sortCode` field on `Account` yet. Explicitly
wanted for the learning value, not just the feature itself — the user wants to design and build
this one properly rather than skip it as unnecessary for a single-user app.

**Why deferred:** wasn't part of this stage's original field list; raised as a "what if" during
design and worth its own pass rather than bolting on mid-stream.

**Key findings from this design pass, to carry forward:**

- **Hashing is the wrong tool for these fields.** Hashing is one-way — the app needs to *display*
  the account number/sort code back to the user (and potentially reuse it, e.g. to hand to someone
  paying you), not just verify a match the way a password hash does. Same reasoning
  `.claude/01-authentication/design-decisions.md`'s "Email storage" section already applied to
  email. The real choice is plaintext vs. **application-level encryption** (reversible).
- **Encryption is the better fit here than it was for email.** Email needed case-insensitive
  lookup and uniqueness, which application-level encryption breaks unless done deterministically
  (weakening the point). Account number/sort code need neither — no lookup, no uniqueness
  constraint — so encrypting them (e.g. AES-GCM via a JPA `AttributeConverter`, key from an env var
  to start, a real KMS/envelope-encryption story later) has none of email's downsides, and is a
  solid hands-on exercise in symmetric encryption and key management, which fits this project's
  primary learning goal.
- **Sort code + account number are a different risk tier from card number + CVV** — worth keeping
  distinct when this is picked up, not treated as one generic "sensitive financial field" problem:
  - Sort code + account number let someone **pay money in**, not take it out — semi-public
    information handed out routinely (payroll, invoices, cheques). Still worth encrypting at rest
    (see above), but the threat it defends against is more "don't leak my bank details in a DB
    dump" than "someone could steal money with just this."
  - Card number + CVV, if ever added (see "Credit cards (full semantics)" above), is a genuinely
    higher tier — this combination can authorize a payment. **CVV specifically should never be
    stored at rest at all** — this mirrors PCI-DSS's explicit prohibition on retaining CVV after
    authorization, not a judgment call to re-weigh later. If a full card number is ever stored for
    personal reference, store only the **last 4 digits** (how every real wallet/saved-card UI
    displays it back), not the full PAN — there's no legitimate "I need the whole card number for
    my own records" use case the way there is for a bank account number.
- **UI masking is a separate, cheap control from the storage decision.** Regardless of
  plaintext-vs-encrypted storage, the frontend should mask these by default (e.g. `••• ••78`) with
  an explicit reveal action — a shoulder-surfing/screen-recording courtesy, not a cryptographic
  control, and worth doing either way.
- **A PIN / step-up re-authentication before revealing was considered and not recommended for now.**
  Real banking apps do this (re-enter a PIN/biometric before showing a full card number), but it's
  a second credential to design from scratch: how it's stored (hashed, since it's verify-only —
  unlike the account fields themselves), rate-limiting repeated attempts, and a recovery path for
  a forgotten PIN. For a single-user, local-only app already sitting behind a 15-minute JWT
  session (`.claude/01-authentication/design-decisions.md`), it's unclear this buys meaningfully
  more protection than the existing login already provides — it reads more like "a password behind
  the password" here than it would in a genuinely multi-user, internet-exposed system. Worth
  revisiting specifically if/when a full card number is ever stored (higher stakes, closer to where
  real banking apps actually use this pattern), but not justified for sort code/account number
  alone.

**What it would need, when picked up:**
- `accountNumber`/`sortCode` columns on `Account` (or wherever card number ends up living),
  encrypted via a JPA `AttributeConverter`, with the encryption key sourced from an env var
  (consistent with how `JWT_SECRET_KEY` is already handled) and a documented path to a real
  KMS/envelope-encryption approach later
- Frontend: masked-by-default display with an explicit reveal toggle on the account detail view
- A decision on where exactly the encryption key lives in a multi-environment setup (dev vs. a
  future deployment target) once that question is real, not guessed at now
- If/when card number is added: last-4-only storage, no CVV capture ever, and a fresh look at
  whether step-up re-auth before reveal is worth it at that point

## Account archiving / closing

**Status: Deferred past this stage** (raised during this design pass, not from an original open
question). Not part of `accounts`. No `archived`/`closedOn` field on `Account`.

**Why deferred:** a real user will eventually close a bank account (switch providers, consolidate
savings) while wanting to keep its transaction history visible for historical spending insight —
distinct from deleting it outright. This is a genuine future need, but not required for the first
pass at creating/viewing/editing accounts and transactions, and the right shape (a soft
`archived` flag that hides an account from "active" views/new-transaction entry while keeping it
and its history readable, vs. something else) deserves its own small design pass rather than a
guess bolted on now.

**What it would need, when picked up:**
- A boolean `archived` (or `closedOn` date) field, plus a decision on whether an archived account's
  balance still participates in any aggregate "net worth" figure (likely not, since the money has
  moved elsewhere) or any spending-insight totals (likely yes, for the period before it closed)
- Frontend: a way to view archived accounts separately from the main active-accounts view, and to
  un-archive if closed in error

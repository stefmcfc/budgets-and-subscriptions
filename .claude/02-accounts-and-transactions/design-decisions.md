# Accounts and Transactions — Design Decisions

Resolves `.claude/00-initial-setup/02-open-questions.md` Q1a's "worth a dedicated design question
once this build step is reached" note, now that it has been (build order step 2). Written
2026-10-23, before `spec-writer` drafts the backend/frontend specs for this stage — intended as
the source `spec-writer` should read for these decisions rather than re-deriving or re-asking them.
Deferred/out-of-scope items raised during this pass live in `spec_candidates.md`, not here.

## Scope for this stage

v1 of this stage covers **plain ledger accounts only**: `CURRENT`, `SAVINGS`, `CASH_ISA`. All
three share one data model — an `Account` with a running balance, holding a series of discrete
`Transaction`s (money in/out, dated, described). This deliberately narrows product.md's full
account-type list (which also names credit cards, Stocks & Shares ISA, pensions, and investment
accounts) down to the subset that actually fits a discrete-transaction ledger without a different
semantic model. The rest are real, tracked future work — see `spec_candidates.md` — not forgotten,
just not this stage's job.

**Out of scope for this stage, cross-referenced, not re-litigated here:**
- Categories, category suggestion, recurring/subscription detection, big-purchase flagging — all
  build order step 3 ("the transaction classification engine"). `Transaction` in this stage gets
  **no** category FK, recurring flag, or classification-related column at all. When step 3 is
  reached, those arrive via a new additive Flyway migration, the same pattern
  `backend_spec_001_basic_login.md`'s deferred email-verification columns already established —
  not something to pre-build a placeholder for now.
- Multi-person running balances (Q8 — money owed by/to other people) — build order step 4. Likely
  a distinct `Person`/ledger concept of its own (Q8 explicitly calls out "cash transactions not
  tied to a bank account," which this stage's `Account`-scoped `Transaction` can't represent) —
  needs its own design pass when that step is reached, not an assumption baked in here.
- Foreign-currency informational flagging (product.md's "may be flagged informationally") — see
  `spec_candidates.md`; product.md itself hedges this as a maybe, not a committed v1 feature.

## Account types (v1)

- **`CURRENT`, `SAVINGS`, `CASH_ISA`** — a Java enum on `Account`. All three behave identically at
  the data-model level in this stage (nothing type-specific yet); the enum exists so the frontend
  can label/group accounts meaningfully and so a type-specific rule (e.g. ISA annual-allowance
  tracking) has somewhere to hang later without a schema change.
- **Credit cards, Stocks & Shares ISA, pensions, investment accounts are explicitly deferred** —
  see `spec_candidates.md` for each one's rationale and what it would need.

## Account fields

- **`bankName`** + **`accountName`** — two free-text fields, not one, per Q1a's own example
  ("Current Account with Bank A"). **Revised 2026-10-23**, during `backend_spec_002`'s hand-coded
  migration review: a single combined `name` field (the original decision) can't be grouped or
  filtered by bank later without parsing free text back apart, which is fragile. Splitting the two
  up front keeps `GROUP BY bank_name`/a future "accounts grouped by bank" view trivial. Both
  required, **no uniqueness constraint on either field or the pair** — a user can have two accounts
  at the same bank, two accounts with the same account-type label at different banks, or (if they
  really want) two identical `bankName`/`accountName` pairs; none of that is this app's business to
  prevent.
  - **A dedicated `banks` table/entity was considered and rejected as overkill for this stage** —
    it would add a new entity, repository, and either an on-the-fly-creation or a pick-from-list
    flow, for a benefit (enforced consistent spelling, room for future bank-level metadata) with no
    concrete need yet. The tradeoff accepted instead: `bankName` is plain free text with no DB-level
    consistency enforcement, so "group by bank" only works as well as the user spells the same bank
    name the same way across their own accounts. Cheap mitigation if this ever actually bites:
    suggest previously-used `bankName` values as autocomplete options in the frontend, no schema
    change required. Promote to a real `Bank` entity later only if a genuine need shows up (e.g. a
    future bank-level feature wanting more than a name).
- **`type`** — the `AccountType` enum above.
- **`openingBalance`** (`BigDecimal`) + **`openedOn`** (date) — how a user adds an account that
  already has money in it: the balance as of a specific date, rather than requiring every
  historical transaction to be re-entered just to arrive at today's correct total. `openedOn` is
  also the earliest date a `Transaction` can be recorded against this account (a transaction
  dated before the account's own opening balance date doesn't have a coherent "starting point" to
  apply against).
- **`balance`** (`BigDecimal`) — see "Running balance," next.
- **Monetary columns use `NUMERIC(19, 2)` in Postgres**, not a smaller precision like `NUMERIC(9,
  2)`. Postgres `NUMERIC` storage is variable-length based on the actual value stored, not the
  declared precision, so this costs nothing extra for a normal balance — `19` is simply a
  conventional "as big as you'd ever plausibly need" ceiling (roughly the range a 64-bit
  integer-cents column would cover), not an arbitrary guess. `2` decimal places (pennies) is
  correct, not `4` — this app is single-currency GBP with no conversion arithmetic happening in it
  (per `product.md`'s Known Constraints), so there's no legitimate sub-penny value to represent.
  Applies to `openingBalance`/`balance` here and to `Transaction.amount` once that spec exists.
- **Owner** — every `Account` carries an owning `User` reference (FK), consistent with
  `product.md`'s "every entity carries an owner/user reference once auth exists" readiness note
  and this app's multi-user design. An account belongs to exactly one user; there is no shared/joint
  account concept in this stage.

## Running balance

**Decision: stored, transactionally-maintained column**, not computed on the fly. `Account.balance`
starts equal to `openingBalance` at creation and is updated inside the *same* database transaction
as every `Transaction` insert/update/delete that affects it — a deliberate choice over the simpler
"always `SUM()` the transactions at read time" approach, specifically because correctly maintaining
a mutable running total under concurrent/overlapping writes is a real, worthwhile thing to practice
hand-coding (this project's primary goal, per root `CLAUDE.md`), not something to engineer away by
avoiding the stored column entirely.

**What this means for the hand-coded service layer** (not a decision being made here — a heads-up
for whoever hand-codes `AccountService`/`TransactionService`, since the Working Agreement reserves
this layer for the human): a stored running balance is only as correct as the concurrency control
around it. Two requests racing to add a transaction to the same account must not both read the old
balance and write their own delta on top of it, silently losing one update (a classic lost-update
bug). The usual fixes — a pessimistic row lock (`SELECT ... FOR UPDATE`, or JPA's
`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the `Account` row for the duration of the transaction
write, or an optimistic approach (a `@Version` column, retrying on `OptimisticLockException`) — are
exactly the kind of "how does a real system keep a mutable total correct" problem worth researching
and hand-coding properly here, rather than something Claude should pre-solve in scaffolding.

**Editing and deleting transactions must also keep `balance` correct**, not just creating one: an
edit needs to reverse the old amount's effect and apply the new one (or compute the net delta), and
a delete needs to reverse that transaction's amount — both under the same concurrency-safety
requirement as creation above.

## Transaction fields

- **`amount`** (`BigDecimal`, signed) — positive = money in, negative = money out. Chosen over a
  separate `type` enum (`CREDIT`/`DEBIT`) + unsigned amount because it makes balance arithmetic a
  plain `balance + amount` at every step, rather than a branch on type; standard convention for a
  ledger-style model. `product.md`'s `BigDecimal`-for-money decision already settled the type
  itself (Q10) — this decision is only about sign convention.
- **`date`** — when the transaction occurred (not when it was entered into the app). Must be on or
  after the owning `Account`'s `openedOn` date (see "Account fields" above).
- **`description`** — free text (merchant/payee/note). No separate structured "merchant" field in
  this stage — Q6/Q7's classification engine (build order step 3) is what will eventually need to
  parse/match against this text for pattern detection, but that's a step-3 concern, not this one's.
- **Account** — every `Transaction` belongs to exactly one `Account` (FK). `Transaction` has **no
  separate owner column of its own** — ownership is transitive through its `Account`, so an
  authorization check only ever needs to confirm "does the current user own this transaction's
  account," never a second, independently-maintained owner reference that could drift out of sync.
- **No currency field in this stage** — see `spec_candidates.md`.

## Spec breakdown

Decided 2026-10-23: **two stages, four specs** (a backend + frontend pair for each), mirroring the
auth feature's own per-stage backend/frontend pairing rather than one monolithic
accounts-and-transactions spec:

1. **`accounts`** — `Account` entity (`AccountType` enum, `bankName`, `accountName`,
   `openingBalance`, `openedOn`, `balance`, owner FK), full CRUD, list/view a user's own accounts
   only.
2. **`transactions`** — `Transaction` entity (`amount`, `date`, `description`, account FK), full
   CRUD scoped to an account the current user owns, and the transactionally-maintained running
   balance described above.

`transactions`'s backend/frontend specs each `Depend on` `accounts`'s backend/frontend specs
respectively — a transaction is meaningless without an account to create it against, and the
frontend needs an account picker/context before it can build a transaction form. Invoke
`spec-writer` once per spec (4 invocations total, `accounts` before `transactions`), pointing it at
this file for the relevant stage's decisions each time.

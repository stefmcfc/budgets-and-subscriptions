# Learnings

Notes-to-self captured while hand-coding the backend, on things that weren't obvious going in.
Append as new ones come up — grouped loosely by topic, not chronological.

## Testing a DB-level constraint means hitting a real DB, not mocking the repository

**Context**: `AUTH-001-AC-01` (`backend_spec_001_basic_login.md`) requires the `User` entity to
enforce a case-insensitive unique constraint on `email` — persisting a second user whose email
differs from an existing one only by case must fail.

**Why a real database, not a mock**: the thing being tested *is* a database-level constraint (a
unique index, case-insensitive via a functional index on `LOWER(email)` or Postgres's `citext`).
A mocked `UserRepository` can't enforce that — it only does whatever behavior you've told it to
return. Testing against a mock here would just prove "my mock does what I configured it to do,"
not that the real constraint exists and works. Some things genuinely require an integration-style
test against the real thing to mean anything.

**Why this doesn't leave junk in the DB between runs**: `@DataJpaTest` (already on the classpath
via `spring-boot-starter-data-jpa-test`) wraps each test method in a transaction that gets rolled
back automatically once the test finishes — Spring's `TransactionalTestExecutionListener`. This
applies the same way under Spock as it would under JUnit, since `spock-spring` wires in the same
Spring TestContext machinery. So a test can persist a user, assert the second (case-variant) email
fails to persist, and then everything gets rolled back before the next test runs — nothing
survives to collide with a later run.

**The one way to break this**: annotating a test (or the spec class) `@Commit`, or otherwise
disabling the default rollback. Do that, and persisted rows really do survive past the test —
at which point a second run legitimately would collide with leftover data from the first. Leave
the default rollback behavior alone and this is a non-issue; no manual cleanup code needed.

**Practical prerequisite**: this kind of test needs the real Postgres *test* database
(`budgetsandsubscriptions_test`, created by `docker/init-test-db.sql` on first container init) up
and reachable — `docker compose up -d` from the repo root, with Docker Desktop's daemon actually
running (see `tech.md`'s Docker Desktop gotcha).

## `save()` doesn't guarantee the `INSERT` has hit the DB yet — flush before asserting

**Context**: `AUTH-001-AC-01`'s test persisted a first user, then persisted a second with a
case-variant email and asserted `thrown(DataIntegrityViolationException)` in the `then:` block.
Switching the test onto the real Postgres test database (`Replace.NONE` + `test-db` profile) was
necessary but not sufficient — the test still reported no exception thrown.

**Why**: `User.id` is `@GeneratedValue` with no explicit strategy, which Hibernate 6 resolves to
in-memory UUID generation — the ID is created in Java before the `INSERT` is ever sent, unlike
`IDENTITY` or a sequence Hibernate needs back immediately. With no DB round-trip required to know
the entity's ID, Hibernate has no reason to flush eagerly; the `INSERT` (and whatever constraint it
would violate) can sit in the session's write-behind queue past the end of the `when:` block.
Spock's `thrown()` only catches what's raised *during* `when:` — if the SQL hasn't actually
executed yet, there's nothing to catch.

**Fix**: call `saveAndFlush(...)` (or `save(...)` followed by `userRepository.flush()`) instead of
plain `save(...)` when the assertion depends on the DB having actually seen the statement — a
unique/check constraint, a trigger, anything enforced server-side rather than in Java.

**Where this bites generally**: any `@DataJpaTest` assertion that depends on a DB-level constraint,
combined with an entity whose PK is generated without a server round-trip (UUID, assigned IDs).
IDENTITY-strategy or sequence-fetched PKs don't have this problem because Hibernate must flush to
get the key back, which incidentally makes the constraint violation surface immediately too — so
this is specifically a UUID/no-round-trip-PK gotcha, not a general `@DataJpaTest` one.

**It's not just DB constraints — Bean Validation (`@NotBlank` etc.) hits the same wall**: hit this
again on `AUTH-001-AC-03` (blank/null `displayName`). Hibernate's Bean Validation integration is
wired in via the pre-insert/pre-update event listeners, which — same as the unique-index check — only
run at flush time, not the moment `save()`/`persist()` is called. With a UUID PK deferring the
flush, `thrown(ConstraintViolationException)` can miss it for the identical reason
`DataIntegrityViolationException` was missed on AC-01. Debugging tell: stepping through and calling
a repository query (e.g. `findByEmail(...)`) from the debugger console *does* throw — because
Hibernate's default `FlushMode.AUTO` auto-flushes pending changes before running a query against the
same session, which isn't what the actual test code does. If the debugger surfaces an exception the
test doesn't, suspect a missing flush before reaching for any other explanation.

## `@Enumerated(EnumType.STRING)`, not `EnumType.ORDINAL`

**Context**: `User.role` needed to map the `Role` enum (`USER`/`ADMIN`) to a database column.

**Why `STRING`, generally**: `ORDINAL` persists the enum's *position* in the Java declaration as
an integer (`USER` = 0, `ADMIN` = 1). That's fragile in a way that corrupts data silently — insert
a new constant before an existing one, or reorder the declaration, and every already-persisted
row's meaning shifts with no error at write or read time. `STRING` persists the literal name
(`'USER'`, `'ADMIN'`), which is unaffected by reordering and is human-readable if the table is ever
queried directly. This is one of the most commonly cited JPA footguns for exactly this reason.

**Why it wasn't actually a choice here**: `V001__create_users_table.sql` already defines the
column as `role text NOT NULL CHECK (role IN ('USER', 'ADMIN'))`. `ORDINAL` would try to write `0`
or `1` into that column, which fails outright — neither value is in the `CHECK` constraint's
allowed set. `STRING` writes exactly what the column already requires. The migration's existing
shape made this the only option that would actually work, not just the safer convention.

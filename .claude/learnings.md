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

## Calling a setter on an entity already loaded in the same transaction is enough to persist it — `save()`/`saveAndFlush()` is only essential for a *new* entity

**Context**: `AccountService.updateAccount` loads the account via
`accountRepository.findByIdAndUserId(...)`, calls setters on it, then calls
`accountRepository.saveAndFlush(account)` before returning — raising the question of whether that
final call does anything `createAccount`'s equivalent call doesn't already cover for free.

**Why `saveAndFlush` is optional here but not in `createAccount`**: an entity returned by a
repository query inside an open `@Transactional` method stays *managed* — still attached to
Hibernate's persistence context for the rest of the method. Hibernate keeps a snapshot of a managed
entity's state from when it was loaded, and any setter call marks it dirty; at the next flush
(automatically, at transaction commit — no explicit call needed) Hibernate diffs current values
against that snapshot and emits whatever `UPDATE` is needed on its own. `createAccount`'s entity, by
contrast, is brand-new and was never in the persistence context — there's no snapshot to diff
against, so `save()` there isn't a flush-timing nicety, it's the only thing that actually calls
`persist()`/issues the `INSERT` at all (and is how the generated ID gets back onto the object).

**Practical consequence**: calling `setXxx(value)` with the *same* value the entity already has is
also already a no-op at the SQL level for free — Hibernate's dirty check is a value comparison
against the snapshot, not "was a setter invoked," so an unchanged field (or an unchanged whole
entity) produces no `UPDATE` regardless of how many setters got called. No manual "is this actually
different" check is needed to avoid a wasted write.

**Takeaway**: keeping an explicit `saveAndFlush` call on a mutated *managed* entity is a legitimate,
deliberate readability choice — it documents "this mutation is meant to be persisted" right at the
point of mutation, and keeps the method's shape consistent with a sibling `create` method — but
it's optional, not required, precisely because the entity never left the persistence context. The
moment an entity genuinely is new or detached, that call stops being optional and starts being the
actual mechanism.

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

## DTO-level Bean Validation for field shape, service layer only for things needing the DB

**Context**: `AUTH-001-AC-07` (password under 12 chars → 400) and `AUTH-001-AC-08` (malformed
email/blank display name → 400) started out as hand-rolled checks — an `AuthService.validateRegistration`
method returning a custom `AuthValidator` result object (`valid` boolean + `Reason` enum),
short-circuiting on the first failure found.

**Why that's unnecessary for these two ACs**: both are pure single-field shape checks — "is this
one field well-formed on its own," with no dependency on persisted state. That's exactly what
Jakarta Bean Validation annotations on the DTO (`@Size(min = 12)` on `password`, `@Email` on
`email`, `@NotBlank` on `displayName`) already do declaratively, triggered by `@Valid` on the
controller's `@RequestBody` parameter. Rolling the same check by hand in the service layer
duplicates framework behavior and does it slightly worse — the hand-rolled version short-circuited
on the first failure, so a request with both a bad password *and* a malformed email would only
ever report the password problem; Bean Validation collects every field violation from one pass.

**Where a custom service-layer check still earns its place**: `AUTH-001-AC-06` (duplicate email,
case-insensitive) isn't a shape check — it needs a `UserRepository` lookup against existing data,
which is genuine business logic. That one can't be a DTO annotation and correctly belongs in
`AuthService`, same as the actual registration orchestration (hash password, save user, issue
token). The dividing line: if checking it requires looking at persisted state, it's the service's
job; if it's just "is this field itself valid," it's the DTO's.

## `@Valid @RequestBody` failures throw `MethodArgumentNotValidException`, not `ConstraintViolationException`

**Context**: writing the `GlobalExceptionHandler` entry for AC-07/AC-08's `400` response, after
switching those checks onto `RegisterRequest`'s Bean Validation annotations.

**Why the exception type matters**: `ConstraintViolationException` is what Hibernate's Bean
Validation integration throws for *method/executable* validation (e.g. a bare `@RequestParam` on
a `@Validated`-annotated controller) or for JPA entity-level validation at flush time — which is
the exact mechanism behind the earlier `User`/`UserSpec` AC-03 flush gotcha above. It is a
*different* validation trigger than the one that fires here. When `@Valid` sits on a
`@RequestBody` parameter, Spring MVC's `WebDataBinder` runs Bean Validation during request-body
binding, and a failure there raises `MethodArgumentNotValidException` instead — catching
`ConstraintViolationException` in `GlobalExceptionHandler` would silently miss it.

**Also worth knowing before writing the handler**: Spring Boot already maps
`MethodArgumentNotValidException` to `400 Bad Request` out of the box (via
`ResponseEntityExceptionHandler`, producing an RFC 7807 `ProblemDetail` body) — so the status-code
half of AC-07/AC-08 is satisfied with zero handler code. A custom `@ExceptionHandler(MethodArgumentNotValidException.class)`
is only needed to get a consistent, custom error-response shape across endpoints instead of
Spring's default `ProblemDetail`, pulling field errors from `BindingResult.getFieldErrors()`
rather than `ConstraintViolationException.getConstraintViolations()`.

## Jakarta Bean Validation's `@NotBlank`/`@NotEmpty` only apply to specific target types — using them elsewhere fails at runtime, not compile time

**Context**: `AccountCreateRequest` (`BACKEND-002-AC-07`..`AC-12`) first had `@NotBlank` on `type`
(an `AccountType` enum) and `@NotEmpty` on `openingBalance` (`BigDecimal`) and `openedOn`
(`LocalDate`), alongside the correct `@NotBlank` on the two `String` fields. The code compiled
fine, and `AccountControllerSpec`'s AC-07 happy-path test started failing with `500` instead of
`201`.

**Why**: `@NotBlank` only validates `CharSequence`; `@NotEmpty` only validates `CharSequence`,
`Collection`, `Map`, or an array. Neither carries any meaning for an enum, a `BigDecimal`, or a
`LocalDate` — there's no "blank"/"empty" concept for any of them, just present-or-absent.
Hibernate Validator doesn't catch this at compile time (annotations are just metadata to `javac`);
it only discovers the mismatch the first time it actually tries to validate an instance of the
class, throwing `jakarta.validation.UnexpectedTypeException` ("HV000030: No validator could be
found for constraint ... validating type ..."). `GlobalExceptionHandler`'s catch-all turns that
into an unhelpful `500`, which made the symptom look like a controller/service bug rather than a
DTO-annotation mismatch.

**Fix**: use `@NotNull` for any field whose type has no "blank"/"empty" concept — an enum, a
number, a date, or anything else that isn't a `CharSequence`/collection-like type. Reach for
`@NotBlank` only on real `String` fields, `@NotEmpty` only when the type genuinely is a
collection/array/map. **General tell**: if a `500` shows up on a request that should cleanly fail
validation with `400`, check whether every Bean Validation annotation on the DTO is even
applicable to its field's declared type before looking anywhere else.

## Jakarta Bean Validation discovers constraints on any getter-*shaped* method, not just real fields — useful for cross-field rules a record's fields can't carry alone

**Context**: `BACKEND-002-AC-19a` needed "at least one of `bankName`/`accountName`/`type` must be
provided" — a rule spanning all three fields of `AccountUpdateRequest`, which no single
`@NotNull`/`@NotBlank` on one field alone can express.

**Why this is possible on a record at all**: Bean Validation doesn't only look at annotated fields
— Hibernate Validator also inspects any method shaped like a JavaBean getter (`isXxx()` returning
`boolean`, or `getXxx()`), the same mechanism that lets it read a real property. A record's
canonical accessors (`bankName()`, `accountName()`, `type()`) don't fit that shape (no `is`/`get`
prefix), but a record can carry *extra* instance methods beyond its canonical ones — so adding
`public boolean isAtLeastOneFieldPresent() { ... }` and putting `@AssertTrue` on *that method* gives
the validator a hook with visibility into all three fields at once, discovered and invoked by
reflection exactly the way a real `@NotBlank` field is — no custom `@Constraint`
annotation/validator class needed.

**The one easy-to-miss requirement**: the method name's *shape* is load-bearing, not just the
annotation — it must start with `is`/`get`, take no arguments, and (for `@AssertTrue` specifically)
return `boolean`. A differently-named method carrying the exact same annotation simply won't be
found.

**Side effect worth handling**: a method named this way also looks like a real bean-getter to
*Jackson*, not just to the validator — without `@JsonIgnore` on it, serializing the record (e.g.
`objectMapper.writeValueAsString(request)` to build a test payload) would include a synthetic
`"atLeastOneFieldPresent": true` property that was never a real field. Add `@JsonIgnore` alongside
`@AssertTrue` on any method used this way.

**Mental model for "no usages" IDE warnings**: this method is never called directly by any of your
own code — same as a `GlobalExceptionHandler`'s `@ExceptionHandler` methods, it's discovered and
invoked by a framework via reflection, triggered here by `@Valid` on the controller's `@RequestBody`
parameter. An IDE flagging it unused is a false positive for the same reason it would be on an
`@ExceptionHandler` method.

## A missing JSON key and an explicit JSON `null` bind to the identical value on a record — Bean Validation can't tell them apart

**Context**: deciding what "a field wasn't provided" should mean for `AccountUpdateRequest` once
`BACKEND-002-AC-17` made it a genuine partial update. Writing `BACKEND-002-AC-19`'s `where:` table,
rows like `[bankName: null]` (one key, explicit `null` value, the other two keys absent) were
expected to test "blank value rejected" — they didn't.

**Why**: for a reference-typed record component, Jackson binds a missing JSON key and an explicit
`"key": null` to the exact same Java value — `null`. There's no way, once the object is
constructed, to tell "the client deliberately said this field is null" apart from "the client never
mentioned this field at all." So `{"bankName": null}` with the other two keys absent produces
`AccountUpdateRequest(null, null, null)` — indistinguishable, at the validation layer, from sending
`{}`. It trips the *all-three-absent* constraint (`BACKEND-002-AC-19a`'s
`isAtLeastOneFieldPresent()`), not the *present-but-blank* one (`BACKEND-002-AC-19`'s
`isProvidedTextFieldsNotBlank()`), because as far as the bound object is concerned, nothing was
actually present.

**Fix / takeaway**: a "blank" check on a `String` field can only ever fire for a genuinely empty or
whitespace-only string (`""`, `"   "`) — never for `null`, since `null` always collapses into "not
provided," not "provided but empty." When writing data-driven tests for a partial-update DTO, keep
"value is `null`" and "value is blank" as separate test groups up front — they're not two flavors
of the same rejection, they exercise two different constraints entirely.

## An invalid enum string in a JSON body fails before Bean Validation ever runs — a different exception type than a missing/blank field

**Context**: `BACKEND-002-AC-10` needs both a missing `type` and a `type` value outside
`CURRENT`/`SAVINGS`/`CASH_ISA` to return `400`. The spec's own rationale already flagged these as
two different failure mechanisms; this came up again confirming it directly instead of assuming
it was already handled.

**Why**: a missing `type` deserializes fine (Jackson just leaves the field `null`), then fails
Bean Validation's `@NotNull` check, raising `MethodArgumentNotValidException` — already mapped to
`400` by `GlobalExceptionHandler`. An unrecognized string like `"NOT_A_REAL_TYPE"` can't be turned
into an `AccountType` at all — Jackson fails *during deserialization*, before `@Valid`/Bean
Validation ever runs, and Spring wraps that as `HttpMessageNotReadableException` instead.
`GlobalExceptionHandler` had no handler for that exception type, so this sub-case fell through to
the catch-all and returned `500`.

**Fix**: add a dedicated `@ExceptionHandler(HttpMessageNotReadableException.class)` mapped to
`400` — the missing-field and invalid-value halves of the same AC genuinely need two different
handlers to both pass. **Testing it**: since the DTO (`AccountCreateRequest`) has no way to
construct an invalid enum value (the enum simply has no such constant), express the invalid
request as a raw `Map` serialized through the already-autowired `ObjectMapper`
(`objectMapper.writeValueAsString(["type": "NOT_A_REAL_TYPE", ...])`) instead of the typed DTO —
the same technique `AC-08`'s client-supplied-owner-id test needed, for the same underlying reason:
the attack surface here is the wire format, not anything the compiler can help construct.

## Declaring a `@PathVariable` as its target type (not `String`) pushes format validation into the framework, for free

**Context**: deciding how to implement `GET /api/accounts/{id}` (`BACKEND-002-AC-15`/`AC-16`) —
whether `accountId` should be a `@PathVariable String`, parsed and validated by hand, or a
`@PathVariable UUID` directly. A regex-constrained `@GetMapping` path pattern (`{id:[0-9a-fA-F]{8}-...}`)
was considered first, as a way to reject a malformed id before the controller even runs.

**Why plain `UUID` beats both alternatives**: Spring's `PathVariableMethodArgumentResolver` already
has a `ConversionService` entry for `String → UUID` (the same general mechanism behind `String →
LocalDate`/`String → Enum` elsewhere in request binding). Declaring the parameter as `UUID` means
Spring converts the path segment before the controller method body runs at all, and raises
`MethodArgumentTypeMismatchException` on failure — handleable centrally in `GlobalExceptionHandler`,
the same single error-handling path every other input-shape failure already goes through
(`MethodArgumentNotValidException` for body validation, `HttpMessageNotReadableException` for an
unparseable enum in the body, both above). The regex-path-pattern alternative fails differently: a
non-matching id never reaches this `@GetMapping` at all, falling through to Spring's own "no handler
found" handling instead — a *second*, less-controlled error path with a different response shape
than the rest of the app's `ApiError` contract, for no real benefit over the typed parameter. A
`String` parameter with hand-rolled parsing/regex duplicates what the framework's converter already
does, puts validation logic inside the controller (against this project's thin-controller
convention), and risks getting the regex itself subtly wrong.

**Fix / general tell**: for any path or request parameter with an obvious target type (`UUID`,
`LocalDate`, an enum, a number), declare the parameter as that type and let the conversion failure
flow into `GlobalExceptionHandler` — reach for `String` + manual parsing, or a route-level regex
constraint, only when there's validation logic beyond "is this the right shape" that the built-in
converter genuinely can't express.

## A `@PathVariable UUID` conversion failure isn't one exception — it's three, and only one of them should be a 400

**Context**: `BACKEND-002-AC-24a`/`AC-24b` — testing `GET /api/accounts/{id}` with a non-UUID,
empty, and blank `accountId` path segment all looked like the same "malformed id" case at first,
all expected to funnel into the `MethodArgumentTypeMismatchException` handler the previous entry
set up. Only the genuinely-present-but-unconvertible string actually does.

**Why there are three different exceptions, not one**:
- **A present, non-UUID-shaped string** (`"not a uuid"`, or the literal text `"null"` — see the
  next entry for why that's the text you actually get) — the path segment matches the route, Boot's
  `StringToUuidConverter` calls `UUID.fromString(...)` on it, which throws `IllegalArgumentException`,
  surfacing as `MethodArgumentTypeMismatchException`. This is the one genuinely "malformed input"
  case — `400` is correct.
- **A genuinely empty segment** (`/api/accounts/` — trailing slash, nothing after it) — doesn't even
  match `/api/accounts/{id}` at all. Trailing-slash matching has been off by default since Spring
  Framework 6.1/Boot 3.2, so this falls through to static-resource resolution instead, which throws
  `NoResourceFoundException` when nothing matches there either.
- **A blank segment** (whitespace only) — *does* match the route (it's a non-empty string of
  spaces), but `StringToUuidConverter` special-cases blank/empty input by returning `null` instead
  of throwing. Spring then runs its own "a required path variable resolved to null" check and throws
  `MissingPathVariableException` — a completely different resolution path from the type-mismatch
  one above, despite both ultimately stemming from "the converter didn't like this input."

**Why this matters beyond just picking the right `@ExceptionHandler`**: `NoResourceFoundException`
already `extends ResponseStatusException` with `404` built in — if this project's own blanket
`@ExceptionHandler(Exception.class)` catch-all weren't present, Spring would already respond `404`
to the empty-segment case for free. The catch-all intercepts it first (it's still an `Exception`,
and there's no more-specific handler registered for it), silently downgrading a correct built-in
`404` into a generic `500`. General tell: a blanket `Exception`-class handler doesn't just risk
masking real bugs as `500`s (per the earlier `DataAccessException` entry) — it can also shadow a
framework-provided `ResponseStatusException` that already knew its own correct status, for any
exception type that extends it and has no explicit handler of its own.

**Takeaway**: "the id is malformed" actually splits into two distinct categories with different
correct responses — *something was supplied but doesn't convert* (`400`) vs. *nothing meaningful was
supplied at all* (`404`, because absence reads the same as a route that doesn't exist) — and they
arrive via three different exception types, not one.

## Groovy's `null` in string concatenation becomes the literal text `"null"` — there's no way to send an actual null in a URL path

**Context**: a `where:` table row for `BACKEND-002-AC-24a` used Groovy's `null` literal as the
`accountId` value, built into the request via `get(ENDPOINT_ACCOUNTS + "/" + accountId)`, intending
to test "no id supplied." The resulting exception read `Invalid UUID string: null` — i.e. the server
received the four-character text `null`, not an absence of input.

**Why**: Groovy's `String + null` resolves to `StringGroovyMethods.plus(String, Object)`, which
calls `String.valueOf(null)` and concatenates that — exactly Java's own behavior for string
concatenation with `null`. The request is built as plain text before it's ever sent, so by the time
it leaves the test, `"/api/accounts/" + null` is indistinguishable from `"/api/accounts/" + "null"`
typed as a literal. This isn't specific to Groovy or MockMvc either — a URL path segment is always a
string on the wire; there is no HTTP-level representation of "a null value" in a path, only *some*
string value or *no segment at all* (an actually empty/trailing-slash path, a genuinely different
case — see the previous entry).

**Takeaway**: when a `where:` table's `null` is meant to represent "no id in the path," check what
it actually becomes once it's concatenated into a URL — it almost certainly isn't absence, it's the
text `"null"`, and will exercise whichever code path handles *that* string, not whichever path (if
any) handles a missing segment.

## A Groovy literal wrapped in outer quotes is a `String`, not the `Map`/`List` it looks like — and `ObjectMapper` will happily double-encode it

**Context**: building `where:`-table JSON payloads for `BACKEND-002-AC-17`'s partial-update test, a
row was written as `"[\"bankName\": \"bank\"]"` — intended as a map literal — then passed to
`objectMapper.writeValueAsString(payload)`. The identical mistake reappeared independently in
`BACKEND-002-AC-19a`'s hand-written test as `String payload = "[]"`, intended as an empty body.

**Why**: the outer pair of `"` makes the whole thing a Groovy `String` literal; its contents only
*look* like a map/list syntactically. `objectMapper.writeValueAsString(aString)` does exactly what
it's told — it serializes a `String` the only way Jackson knows how, by quoting and escaping it as a
JSON string. The result is a JSON *string* token (`"[\"bankName\": \"bank\"]"`), not a JSON *object*
(`{"bankName":"bank"}`). When that hits a controller expecting an object-shaped DTO, Jackson fails
with `MismatchedInputException: ... no String-argument constructor/factory method to deserialize
from String value (...)` — an error that names the DTO, not the real cause several steps upstream.

**Fix**: write the literal as a genuine Groovy `Map`/`List`, with no surrounding quotes or escaping —
`[bankName: "bank"]` instead of `"[\"bankName\": \"bank\"]"`, `[:]` instead of `"[]"` for an empty
object. **General tell**: if `objectMapper.writeValueAsString(x)` is being called on something
that's supposed to represent a JSON object/array, check `x`'s actual Groovy type isn't secretly
`String` before looking anywhere else.

**Danger of a false-positive pass**: this exact bug in the `AC-19a` test didn't fail loudly — it
still returned `400` as asserted, just via `HttpMessageNotReadableException` (malformed body)
rather than the `@AssertTrue` constraint the test was actually meant to exercise. The test was green
for the wrong reason, and would have stayed green even if the real validation rule it was supposed
to protect were deleted entirely. A passing status-code-only assertion doesn't prove the *intended*
code path ran — only that some code path produced that status.

## `Stream.toString()` doesn't describe its elements — collect it before putting it in a response

**Context**: `GlobalExceptionHandler.handleMethodArgumentNotValidException` built its error message
with `ex.getBindingResult().getFieldErrors().stream().toString()`, intending to list the actual
field/message pairs for the client.

**Why it silently produces garbage instead of failing loudly**: `Stream` has no overridden
`toString()` — calling it falls back to `Object`'s default, which prints the class name and
identity hash (`java.util.stream.ReferencePipeline$3@1b6d3586`), not the stream's contents. It
compiles cleanly and returns a non-null `String`, so nothing about the mistake is visible at the
call site — and it went unnoticed because the existing tests for that code path (`AC-07`/`AC-08`)
only ever asserted the HTTP status, never inspected the response body's message content.

**Fix**: terminate the stream with an actual collector before using the result:
```java
ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.groupingBy(FieldError::getField,
                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
```
**General tell**: any time a `Stream` (or another type with no meaningful `toString()`, like most
functional-interface types) ends up directly in a message/log/response without an intervening
`.collect()`/`.forEach()`/terminal operation, that's worth a second look — and worth a test that
actually inspects the content produced, not just the status/outcome around it.

## `@WebMvcTest` and `@DataJpaTest` can't be stacked on one spec class

**Context**: `AuthControllerSpec` started out with both `@WebMvcTest(controllers = AuthController)`
and `@DataJpaTest` (plus `@AutoConfigureTestDatabase`/`@ActiveProfiles("test-db")`) on the same
class, copied over from `UserSpec`'s habit of needing the real test database.

**Why it fails outright, not just awkwardly**: both are test-slice annotations, and each carries
its own `@BootstrapWith` meta-annotation naming a different `TestContextBootstrapper`
(`WebMvcTestContextBootstrapper` vs `DataJpaTestContextBootstrapper`). Spring's test framework only
allows one bootstrapper per test class — stacking them throws
`IllegalStateException: found multiple declarations of @BootstrapWith` at collection time, before
any test even runs. This isn't a tunable conflict; the two slices boot fundamentally different,
narrower contexts (`@WebMvcTest` = web layer only, no `@Service`/`@Repository` beans;
`@DataJpaTest` = persistence layer only, no web layer) and can't be merged into one.

**The fix is to not need both**: `AuthController` doesn't touch the database directly — it
delegates to `AuthService` — so `AuthControllerSpec` only needs `@WebMvcTest` with `AuthService`
mocked out. Testing against a real database belongs in whichever spec is actually exercising
DB-dependent logic (`UserSpec` for entity/constraint behavior; `AuthServiceSpec`, if it needs one,
for the business logic that calls the repository) — never in the controller spec.

## Mocking a Spring-managed bean under `@WebMvcTest`, the Spock-native way

**Context**: `AuthControllerSpec` needs `AuthService` replaced with a mock so the Spring context
can wire something into `AuthController` at all (`@WebMvcTest` doesn't load real `@Service` beans).

**Why not `@MockitoBean`**: it works (it's Spring Boot's own mechanism for this), but it pulls in
Mockito's stubbing/verification API (`when(...).thenReturn(...)`) into an otherwise all-Spock test
suite. `spock-spring` (already a dependency here) has its own equivalent: `@SpringBean`
(`org.spockframework.spring.SpringBean`) registers a Spock `Mock()`/`Stub()` as the bean Spring
injects into the controller, so stubbing/verification stays in native Spock syntax (`authService.register(_) >> result`,
`1 * authService.register(_)`) throughout.

**The one real gotcha**: the mock must be created at field-initialization time, not in `setup()`:
```groovy
@SpringBean
AuthService authService = Mock()
```
`spock-spring`'s extension needs the mock instance in hand *before* the context is built, so it can
register it as the bean definition. Initializing it inside `setup()` is too late in the lifecycle —
the context has already been (or is being) constructed by then.

## `@WebMvcTest` silently drops `@Value` resolution, and a `src/test/resources` yaml replaces main's rather than merging with it

**Context**: `AUTH-001-AC-05` — `AuthController`'s constructor takes `@Value("${app.cookie.expiry-minutes}") int expiryMinutes`, and running `AuthControllerSpec` threw `NumberFormatException: For input string: "${app.cookie.expiry-minutes}"`.

**Why the error looks like that instead of a clear "property not found"**: `@WebMvcTest` disables Spring Boot's full auto-configuration (`@OverrideAutoConfiguration(enabled = false)`) and only re-imports a curated, MVC-specific subset — confirmed by checking the actual `*.imports` files in `spring-boot-webmvc-test`. `PropertyPlaceholderAutoConfiguration` (which registers the `PropertySourcesPlaceholderConfigurer` bean that makes `${...}` substitution happen at all) isn't in that subset. With no such bean, `@Value`'s embedded-value resolution silently no-ops and hands the *literal* unresolved string to the constructor, which then fails type conversion to `int` — a confusing symptom for what's really a missing-bean problem.

**Fix, step one**: explicitly bring the autoconfiguration back for just this slice:
```groovy
@ImportAutoConfiguration(PropertyPlaceholderAutoConfiguration)
```
This turns the misleading `NumberFormatException` into the real, honest error: `PlaceholderResolutionException: Could not resolve placeholder 'app.cookie.expiry-minutes'`.

**Fix, step two — the actual root cause that error pointed at**: `src/test/resources/application.yaml` and `src/main/resources/application.yaml` aren't merged on the test classpath — the test one entirely *shadows* main's. This project's test yaml only had `spring.datasource`/`spring.jpa` overrides for the test DB, no `app:` section at all, so `app.cookie.expiry-minutes` genuinely didn't exist anywhere in the test `Environment`. Adding the needed `app.*` keys to `src/test/resources/application.yaml` fixed it for real.

**General takeaway**: when a project has both a main and a test `application.yaml`, assume the test one *replaces* the whole file rather than supplementing it — any property a test-loaded bean needs via `@Value` has to exist in the test yaml too, not just main's.

**Dead ends that looked plausible but weren't it**: `@EnableAutoConfiguration` reopens the *entire* default auto-configuration list (real datasource, Flyway, etc.), defeating the point of a narrow web slice test. `@ImportAutoConfiguration(AuthController)` doesn't make sense either — that annotation is specifically for importing `@AutoConfiguration`-shaped classes, not arbitrary own components; plain `@Import` (already used for `SecurityConfig` in this same spec) is the right tool for that.

## Renaming the test `application.yaml` to a profile-specific name silently opts it out of any test that doesn't activate that profile

**Context**: `src/test/resources/application.yaml` (the file the previous entry's "general
takeaway" was about) got renamed to `application-test.yaml`, split out alongside a new
`application-test-db.yaml`, so DB-needing specs could layer `@ActiveProfiles("test-db")` on top.
Afterwards, the plain `@SpringBootTest` smoke test (`BudgetsAndSubscriptionsApplicationTests
.contextLoads()`) started failing with `PlaceholderResolutionException: Could not resolve
placeholder 'JWT_SECRET_KEY' ... <-- "${app.jwt.secret}"`, even though nothing about JWT
configuration had changed.

**Why**: `application-{profile}.yaml` is Spring Boot's profile-*conditional* naming convention —
it's only loaded when that exact profile is active. A plain `application.yaml` (no profile suffix)
is unconditional and loads for every test, which is what made the previous entry's "test yaml
replaces main's for every test" takeaway true at the time. The rename changed that: nothing in the
project ever activates a profile literally named `"test"` — the specs that need real persistence
use `@ActiveProfiles("test-db")`, which loads `application-test-db.yaml` (datasource/JPA settings
only) instead. So `application-test.yaml` — holding the `app.jwt.secret` fallback and the
`app.cookie`/`app.cors` keys — became dead weight, loaded by nothing. `contextLoads()` boots the
full, unsliced context with no profile at all, so it falls through to `main`'s `application.yaml`,
where the JWT secret's default had separately been removed on purpose (forcing real environments to
supply one) — and the full context can't start without it.

**Why the other specs didn't catch this**: each test that still passed sidesteps `app.jwt.secret`
entirely rather than resolving it: `AuthControllerSpec` (`@WebMvcTest`) replaces `JwtService` with a
`@SpringBean` mock, so its real constructor (and the `@Value` on it) never runs; `AuthServiceDataJpaSpec`/
`UserSpec` (`@DataJpaTest`) only load the JPA slice, which never touches `app.jwt.secret` at all.
A plain `@SpringBootTest` with no test slice is the *only* place that actually resolves every
`@Value` in the whole app — which is exactly why it was the only test positioned to notice.

**Fix**: add `@ActiveProfiles("test")` to `BudgetsAndSubscriptionsApplicationTests` so it actually
loads `application-test.yaml` again.

**General takeaway, updated**: a test-side `application.yaml` only unconditionally shadows main's
while it's still literally named `application.yaml`. The moment it's renamed to
`application-{profile}.yaml` for any reason (even a good one, like splitting out DB-specific
overrides), every test that doesn't explicitly activate that profile silently stops seeing it —
worth grepping for every `@SpringBootTest`/test-slice annotation in the project and checking each
one still ends up with the properties it actually needs, rather than assuming the rename was purely
additive.

## "No user created" has to be verified at whichever layer the service call actually happens (or doesn't)

**Context**: `AUTH-001-AC-06` (duplicate email → 409) and `AUTH-001-AC-07`/`AUTH-001-AC-08`
(validation failures → 400) all include a "...and shall not create a user" clause, but the right
place to prove that clause turned out to be different for each.

**AC-06**: `AuthService.register(...)` genuinely gets called — it has to run the duplicate-email
check. So "nothing was persisted" can only be proven one layer down, in `AuthServiceSpec`, by
asserting the mocked repository's `save` was never reached:
```groovy
then:
    thrown(DuplicateEmailException)
    0 * userRepository.save(_)
```

**AC-07/AC-08**: `@Valid` validates the DTO during request-body binding, *before* Spring invokes
the controller method at all — a failing constraint means `AuthController`'s handler body (and
therefore the call to `authService.register(...)`) never runs. That makes the assertion simpler and
belongs one layer up, right in `AuthControllerSpec`:
```groovy
then:
    response.andExpect(status().isBadRequest())
and:
    0 * authService.register(_)
```
Here, zero calls to the mocked service is sufficient on its own — since the mock stands in for
everything downstream (hashing, repository, persistence), proving it was never invoked proves
nothing downstream ever ran. No separate service-layer test needed for this half of either AC.

**The general rule**: find the layer where the rejection actually happens, and assert "no side
effect" as "the next layer down was never called" at that exact point — not reflexively at the
controller for every AC, and not by trying to observe real persistence state when everything below
the rejection point is a mock anyway.

## Default Spring Security lock-down blocks `MockMvc` calls until a real `SecurityConfig` exists

**Context**: `AuthControllerSpec` got `403 Forbidden` on every `POST`, with `AuthController` never
being reached at all — before any `SecurityConfig` had been written.

**Why**: with `spring-boot-starter-security` on the classpath and no custom `SecurityFilterChain`
bean yet, Spring Boot auto-configures a default filter chain that locks down every endpoint and
enables session-based CSRF protection. A `POST` from `MockMvc` carries no CSRF token, so the
default CSRF filter rejects it with `403` before the request ever reaches the controller — this is
exactly what the real running app would do too, not a `MockMvc`-only quirk.

**The fix is two real production decisions, not a test workaround**: don't weaken security for
tests — `@WebMvcTest` pulls in the real `SecurityConfig` from `main`, which is the whole point
(testing against the actual filter chain, not a stand-in one). What was actually missing:
- `/api/auth/**` needs `.permitAll()` — registration/login can't require being already
  authenticated.
- CSRF protection needs disabling in the filter chain, because this project's chosen CSRF defense
  is `SameSite=Strict` on the cookies (`design-decisions.md`'s "Transport & CSRF"), not Spring's
  token-based mechanism. Leaving Spring's default CSRF protection on means fighting a mechanism
  the design already decided not to use.

## Spring Security's `authenticated()` check runs in the filter chain, before `DispatcherServlet` — a cold controller breakpoint isn't necessarily a code bug, and `@RestControllerAdvice` can't catch what a filter throws either

**Context**: debugging a `401` on `GET /api/auth/me` where a breakpoint inside
`AuthController.getCurrentUser` never triggered, even attached in the debugger. Root cause:
`SecurityConfig`'s `.requestMatchers("/api/**").authenticated()` rejects the request before Spring
MVC ever routes to a controller — and at the time, nothing in the app populated the
`SecurityContext` from the request's cookie (the controller was parsing it manually itself,
entirely bypassing Spring Security's own mechanism).

**Why the breakpoint never hits**: Spring Security's filter chain is a genuinely separate layer
that runs *before* `DispatcherServlet` even starts routing. `.authenticated()` is evaluated against
whatever `Authentication` is already sitting in `SecurityContextHolder` at that point — if nothing
populated it, the request is rejected by Spring Security's own `AuthenticationEntryPoint` right
there, and the controller method is never invoked. A quiet `401` with no controller-side logging
and a cold breakpoint is the normal symptom of this, not evidence the handler code itself is wrong.

**The same boundary trips up exception handling, not just debugging**: `@RestControllerAdvice`/
`@ExceptionHandler` methods are scoped to exceptions thrown while `DispatcherServlet` is handling a
controller method (including validation/binding). They cannot see an exception thrown inside a
servlet `Filter` — filters run earlier in the chain, outside Spring MVC's dispatch entirely. Moving
JWT-parsing logic out of the controller and into a custom `OncePerRequestFilter` (so something
actually populates `SecurityContext` before the `authenticated()` check runs) means any exception
the parsing throws (`JwtParserException`, or jjwt's own `ExpiredJwtException`/`SignatureException`/
`MalformedJwtException`) has to be caught *inside the filter itself* — `GlobalExceptionHandler`'s
existing mappings for those exact exception types quietly become dead code the moment the only
caller moves into a filter, even though the exception types and messages haven't changed at all.

**Takeaway**: when a route needs Spring Security's own `authenticated()` gate to actually pass,
something has to run *before* that check and explicitly set an `Authentication` into the
`SecurityContext` (a custom filter registered via `addFilterBefore`, or one of Spring Security's own
built-in mechanisms) — manual per-controller parsing of a cookie/header isn't equivalent, no matter
how correct its own logic is, because it runs too late in the pipeline to satisfy a security-layer
rule. And once that parsing logic lives in a filter rather than a controller, its exception handling
has to move with it — it can't stay behind in `GlobalExceptionHandler`.

## `MockMvc` absorbs exceptions into the response — `thrown()` never sees them

**Context**: tried `thrown(MethodArgumentNotValidException)` in `AuthControllerSpec`'s `then:`
block after a `mockMvc.perform(...)` call, expecting it to catch the validation failure.

**Why it can't work**: `MockMvc` simulates the *entire* Spring MVC dispatch, including exception
resolution — `DispatcherServlet` catches `MethodArgumentNotValidException` internally and resolves
it (via Spring's default handling or a `@ControllerAdvice`) into an HTTP response before
`perform()` ever returns control to the test. By the time the test sees anything, the exception has
already been fully converted into a status code and body — nothing propagates out for `thrown()`
to catch, because `thrown()` only works for exceptions that genuinely escape the code called
directly inside `when:`.

**What to assert instead**: the response itself —
```groovy
response.andExpect(status().isBadRequest())
```
— or, to confirm specifically *which* exception triggered it (useful when multiple validation
paths could all produce the same status), inspect the resolved exception on the `MvcResult`:
```groovy
result.resolvedException instanceof MethodArgumentNotValidException
```

## A CORS mapping needs to cover the same URL scope `SecurityConfig` protects, not just today's endpoints

**Context**: `SecurityConfig`'s `securityFilterChain` already wires in a `CorsConfigurationSource`
bean and protects all of `/api/**` behind authentication — but the first `CorsConfig` registered
the actual `CorsConfiguration` against two literal, enumerated paths (`/api/auth/login`,
`/api/auth/register`) instead of the same `/api/**` scope.

**Why it matters beyond today**: it compiled and worked for the two auth endpoints that existed,
but the moment a new controller (accounts, transactions, etc. — next up per the build order) adds
endpoints under `/api/**`, those get no CORS mapping at all. A credentialed cross-origin request to
them has nothing to match against in `UrlBasedCorsConfigurationSource`, so the browser blocks the
real request after a failed preflight — even though Spring Security would otherwise authorize it
fine. Scoping the CORS registration to the same pattern the security filter chain already uses
avoids having to remember to add a line here for every future controller.

## Spock's mock stubbing matches a literal object argument by `equals()`, not field-by-field

**Context**: `AuthServiceSpec`'s "new user" test stubbed `mockUserRepository.save(newUser) >> newUser`,
where `newUser` was a `User` instance built in the test. The real code path (`AuthService.register`)
builds its *own*, separately-constructed `User` with the same field values and passes that to
`save(...)`. The stub never matched, and `savedUser` came back `null`.

**Why**: Spock's interaction matching on a literal argument value checks `equals()` between the
stubbed value and the real invocation's argument. `User` is a JPA entity with no `equals()`/
`hashCode()` override, so it falls back to `Object`'s identity equality — two separately-`new`'d
`User`s with identical fields are never "equal," no matter how alike their data is. An unmatched
stub on a mock returns the type's default (`null` for an object return type), which is why the
symptom looked like "the repository didn't save anything" rather than a clear mismatch error.

**Fix**: match on a wildcard instead of a specific instance — `mockUserRepository.save(_ as User) >> ...`
— when the test doesn't actually care *which* `User` instance gets passed, just that *some* `User`
was passed and what should come back.

## Spock mocking a concrete class keeps the real method's declared return type — stubbing with the wrong type throws at the call site

**Context**: once `User` itself was mocked (`User mockUser = Mock()`) to get around the `equals()`
problem above, an early version stubbed `mockUser.role >> "USER"` — a plain `String` — even though
`User.getRole()` is declared to return the `Role` enum.

**Why this isn't just loose Groovy typing**: Spock mocks a concrete class (not an interface) by
generating a real subclass that overrides each method with its *exact* original signature —
`getRole()` on the generated mock subclass is still declared to return `Role`. The stubbed value
has to be shoved through that same return type at the bytecode level, so handing back a `String`
produces a `ClassCastException` the moment the real code calls `savedUser.getRole()` — not a silent
pass-through, and not caught by Groovy's dynamic typing on the stubbing line itself.

**Fix**: stub with a value of the actual declared type (`mockUser.role >> Role.USER`), and let
whatever `.toString()`/conversion the real code already does downstream produce the `String` form
if that's what the final assertion needs.

## A cardinality-only interaction with no `>>` silently overrides an earlier stub's response

**Context**: `AuthControllerSpec`'s new "accepts registration with password that is... at least 12
characters" test reused the same `setupMockForSuccessfulRegistration(...)` helper AC-05 uses (which
stubs `mockAuthService.register(registerRequest) >> validResponse`), but *also* added
`1 * mockAuthService.register(_ as RegisterRequest)` in the `then:` block to verify the call
happened. The response came back `null` instead of `validResponse`, even though the stub looked
identical to AC-05's.

**Why**: these are two separate interactions on the same mock method, not one combined interaction.
When an invocation could match more than one declared interaction, Spock doesn't merge them — it
picks whichever is still eligible, giving precedence to the **most recently declared** one (this is
intentional, so a generic stub declared early can be overridden by something more specific declared
later). The `then:`-block interaction is textually later than the helper's stub, so Spock matches
the call against it first — and because that interaction has no `>>` at all, its response defaults
to `null`, discarding the stub's `validResponse` entirely.

**Fix**: don't split a stub and a call-count check into two separate interactions for the same
method call — combine them into one: `1 * mockAuthService.register(_ as RegisterRequest) >>
validResponse`. If the call-count assertion isn't actually load-bearing for the test (the response/
status already proves the call happened), it's simplest to just not add it and keep the single stub,
the way AC-05 does.

## A Spock `Mock()`'s unstubbed call returns a silent default, not an error — an unexpected `null` downstream means "check every method actually invoked has a stub"

**Context**: `AuthControllerSpec`'s `AUTH-001-AC-11` test posts to `/api/auth/login`, but its
`given:` block stubbed `mockAuthService.register(_ as RegisterRequest) >> { throw new
DuplicateEmailException(exceptionMessage) }` — copy-pasted from AC-06's registration test.
`authService.login(...)` is what the controller actually calls, so it had no matching interaction
at all; the mock silently returned `null` for it, and `AuthController.login()` NPE'd on
`response.id()` instead of ever reaching the `401` the test expected.

**Why this earns its own entry beyond "fix the copy-paste"**: this is the second distinct mechanism
in this project where a Spock `Mock()`'s *default, silent* response for an unstubbed or unmatched
call (`null`, for any object return type) surfaced as a confusing failure several layers
downstream — an NPE/`500` from production code — rather than an obvious "no interaction matched"
error from Spock itself (the other was the cardinality-only-interaction-with-no-`>>` entry above).
Spock doesn't fail loudly when a call matches nothing; it just hands back the type's default and
lets whatever calls it next discover the problem.

**Fix / general tell**: when a `MockMvc`-driven test gets an NPE or an obviously-wrong value
immediately after the call, before reaching for any other theory, check that *every* method the
real code path actually invokes (not just the one the last similar test happened to stub) has a
matching interaction — a mismatched method name, wrong argument matcher, or wrong mock entirely all
look identical from the outside: a silent `null`.

## `thrown(X)` must be a bare top-level statement — chaining `.message` onto it directly is a compile error

**Context**: `AuthServiceSpec`'s duplicate-email/login-failure tests originally wrote
`thrown(DuplicateEmailException).message == AuthService.MESSAGE_DUPLICATE_EMAIL` as a single line
(after collapsing what looked like a redundant earlier `thrown(DuplicateEmailException)` statement
above it). This fails to compile: `Exception conditions are only allowed as top-level statements.`

**Why**: Spock only recognizes `thrown(SomeException)` as its special "exception condition" syntax
when the call is the *entire* top-level statement, verbatim. The moment anything is chained onto it
(`.message == ...`), it's no longer the whole statement — it's a sub-expression (the receiver of
`.message`) — and Spock's AST transform explicitly rejects that shape rather than silently treating
it as an ordinary method call.

**Fix**: capture the exception into a local variable with a bare `thrown(...)` call, then assert on
the variable separately:
```groovy
then: "DuplicateEmailException is thrown"
    def ex = thrown(DuplicateEmailException)
    ex.message == AuthService.MESSAGE_DUPLICATE_EMAIL
```

## A Spock block label without its colon isn't a compile error — it's silently reinterpreted as a method call, failing only at runtime

**Context**: a parameterized `AuthControllerSpec` test wrote `and "validation errors are returned"`
(no colon) instead of `and: "validation errors are returned"`. The spec compiled fine; every
iteration of the test failed at runtime with `MissingMethodExceptionNoStack` pointing at that exact
line.

**Why**: Spock's AST transform recognizes a block label (`given:`/`when:`/`then:`/`and:`/`expect:`)
by looking for an actual Groovy statement *label*, which requires the colon. Without it, `and "..."`
is just an ordinary Groovy statement: a call to a method/variable named `and`, passed the string as
its sole argument (Groovy's command-expression syntax permits calling a method without parens).
Groovy doesn't verify a method actually exists at compile time (only `@CompileStatic`/`@TypeChecked`
code does), so this compiles — and only blows up when that line executes and there's no `and()`
method to call.

**Fix / general tell**: if a Spock spec throws `MissingMethodException`/`MissingMethodExceptionNoStack`
naming one of the block-label words (`given`, `when`, `then`, `and`, `expect`) at a line that looks
like a label, check for a missing colon before looking anywhere else — it's a one-character typo
that silently downgrades from a would-be compile error into a confusing runtime one.

## An uncaught exception from `when:` fails the test immediately, no matter what the `then:` block was actually trying to check

**Context**: an `AUTH-001-AC-18` test (mixed-case email normalized before the `findByEmail` call)
stubbed `mockUserRepository.findByEmail(...) >> Optional.empty()` — just to keep the interaction
simple, with no intention of testing login failure at all — then failed with an unhandled
`LoginException` thrown from inside `AuthService.login`. The confusion: "I'm not checking the
result of the method, so why is this being flagged?"

**Why**: whether the test inspects a return value is irrelevant — if the `when:` block throws *any*
exception that isn't explicitly expected via `thrown(...)` in `then:`, Spock fails the whole
feature immediately with that exception, before any other `then:` condition (including interaction
verifications) even gets evaluated. And the exception here was genuinely correct given the stub:
telling `AuthService.login` "no user exists for this email" makes it throw `LoginException`,
exactly as real login-not-found behavior should — the test just hadn't acknowledged that its own
setup would trigger it.

**Fix / general tell**: whenever a stub's return value would legitimately cause the production code
to throw (even if that's not what the test is actually trying to exercise), add `thrown(...)` to
`then:` alongside whatever else is being asserted — Spock requires every exception to be accounted
for, not just the ones the test is specifically interested in:
```groovy
then: "call is made with lower case email, and login fails (no matching user)"
    1 * mockUserRepository.findByEmail(MIXED_CASE_EMAIL.toLowerCase()) >> Optional.empty()
    thrown(LoginException)
```

## BCrypt is non-deterministic — never assert equality against a freshly-computed hash

**Context**: `AuthServiceDataJpaSpec` (`AUTH-001-AC-05`, persisted-password-is-not-plaintext) tried
asserting `user.get().getPasswordHash() == BCryptPasswordEncoder.encode(DUMMY_PASSWORD)` — on top of
calling `encode` as if it were static (it's an instance method on `PasswordEncoder`), this would
still fail even once fixed to call it on a real instance.

**Why**: every call to `BCryptPasswordEncoder.encode(...)` generates a fresh random salt and bakes
it into the returned hash string. Two calls encoding the *same* plaintext produce two *different*,
both individually valid, encoded strings. Comparing the stored hash to a second, independently
computed `encode(...)` call with `==` fails almost all the time — not because the code is wrong, but
because the assertion itself assumes a determinism BCrypt deliberately doesn't have.

**Fix**: assert via `PasswordEncoder.matches(rawPassword, encodedHash)` instead, which re-derives the
check using the salt already embedded in the stored hash:
```groovy
new BCryptPasswordEncoder().matches(DUMMY_PASSWORD, user.get().getPasswordHash())
```
General rule: any hashing scheme that embeds a random salt (BCrypt, Argon2, scrypt, ...) can never be
tested by equality against a second hash of the same input — only by the scheme's own verification
method.

## Negating a Hamcrest matcher needs `Matchers.not(...)`, not Groovy's `!`

**Context**: `AuthControllerSpec` (`AUTH-001-AC-09`) tried
`result.andExpect(!content().string(containsString(MOCK_JWT)))` to assert the response body does
*not* contain the raw JWT.

**Why it doesn't do what it looks like it does**: `content().string(containsString(...))` returns a
`ResultMatcher` object, not a boolean. Groovy's `!` only negates that object's *truthiness* — and
Groovy truth for an arbitrary non-null object with no custom `asBoolean()` is always `true` — so
`!matcher` just evaluates to a plain `false`, discarding the matcher entirely. It's not negating the
matching logic at all; it silently produces the wrong kind of value for `andExpect` to use.

**Fix**: compose the negation into the matcher itself with Hamcrest's `not(...)`:
```groovy
result.andExpect(content().string(not(containsString(MOCK_JWT))))
```
Needs `not` added to the static Hamcrest import alongside `containsString`. General rule: any
Hamcrest matcher gets negated by wrapping it in another matcher (`not`), never by applying a
language-level boolean operator from the outside.

## Hamcrest's `hasSize` only matches a `Collection` — a JSON object deserializes to a `Map`, which needs `aMapWithSize`

**Context**: an `AC-19` test asserted `jsonPath('$.details', hasSize(errorCount))` against a
response body shaped like `{"email": ["must not be blank"]}` — a JSON *object*, not an array.

**Why it fails rather than just reading the wrong size**: `$.details` resolves (via Jayway
JsonPath, through `jsonPath()`) to a `LinkedHashMap`, since the JSON value at that path is an
object. Hamcrest's `hasSize` is typed to `Collection` and checks `instanceof Collection` before
anything else — a `Map` isn't a `Collection` (even though it has its own `.size()`), so the match
fails immediately with `Expected: a collection with size <N> but: was LinkedHashMap <{...}>`, not a
size mismatch.

**Fix**: use Hamcrest's Map-flavored equivalent instead — `org.hamcrest.Matchers.aMapWithSize(int)`
— when the thing being measured is a JSON object (deserializes to a `Map`) rather than a JSON array
(deserializes to a `List`/`Collection`).

## A `where:` table can't hand a mock a closure that closes over a `given:` local — and a closure *value* passed to `>>` isn't auto-invoked anyway

**Context**: an early `AUTH-001-AC-09` draft put per-scenario behaviour directly in the `where:`
table as closures, then tried to wire one in as the mock's response:
```groovy
given:
    def validResponse = new RegisterResponse(...)
    mockAuthService.register(newRegisterRequest) >> registerResponse   // registerResponse is a where: value

where:
    scenario   | registerResponse
    "fails"    | ({ RegisterRequest r -> throw new DuplicateEmailException("x") })
    "succeeds" | ({ RegisterRequest r -> validResponse })
```
This fails twice over, in sequence.

**Why #1 — `GroovyCastException`**: Spock only treats a closure as a *dynamic response generator*
when it's written literally at the `>>` call site. A closure arriving via a variable (even one whose
value happens to be a closure) is just an ordinary return value to Spock — it tries to cast that
closure instance directly to the mocked method's declared return type and blows up.

**Why #2 — `MissingPropertyException` on `validResponse`**: `where:` and `given:` are sibling
blocks, not nested. The data table (including any closure literals used as its values) is compiled
and evaluated to produce each iteration's row *before* that iteration's `given:` runs. A closure
sitting in the `where:` table can only close over spec-class fields or other `where:`-scoped data —
never a `def` local created inside `given:`, even though it's textually "right there" in the same
feature method.

**Fix**: don't put behavior-as-closures in a `where:` table at all. Either wrap with a closure
literal at the interaction site that manually `.call()`s the stored one (works around #1, still
can't see `given:` locals so doesn't fix #2), or — simpler and what was actually used here — drop
the parameterized/closure approach entirely and fold the per-scenario assertions directly into the
existing positive and negative specs (`AUTH-001-AC-05`/`06`/`07`/`08`), each already building its own
`given:`-local response/exception the normal way.

## JJWT 0.12.x: `signWith`'s argument order flipped, and `.compact()` (not `.build()`) produces the token string

**Context**: building `JwtService` against `jjwt-api:0.12.6`, `.signWith(Jwts.SIG.HS256, secretKey)`
failed to resolve, and `.build()` didn't return a `String`.

**Why**: the legacy JJWT API (pre-0.12) was `signWith(SignatureAlgorithm, Key)` — algorithm first.
0.12.x's `JwtBuilder.signWith` overload takes `(SecretKey, SecureDigestAlgorithm<?,?>)` —
**key first, algorithm second**. Easy to get backwards if a search turns up older JJWT examples.
Separately, `Jwts.builder()...` returns JJWT's own builder type throughout the chain; the method
that actually serializes it into the final compact JWS string is `.compact()`, not `.build()` (which
doesn't exist on this interface for that purpose) — assigning the result of a chain ending in
anything but `.compact()` to a `String` won't type-check.

## JJWT's builder only has dedicated setters for JWT's *registered* claims — custom claims always go through `.claim()`/`.claims()`

**Context**: `JwtService.createJWT` originally built its payload entirely through
`claims.put("sub", userId); claims.put("role", role);` passed to `.claims(claims)`. Came up when
deciding whether to switch the `sub` entry to the builder's dedicated `.subject(userId)` method
instead.

**Why `.subject(...)` and `.claims(map)` produce an identical token for `sub`, but `role` has no
equivalent**: RFC 7519 defines a fixed set of *registered* claims — `iss`, `sub`, `aud`, `exp`,
`nbf`, `iat`, `jti` — and JJWT's builder has a typed convenience method for each one
(`.issuer()`, `.subject()`, `.expiration()`, etc.). Those methods just write the same key into the
same underlying claims map the generic `.claims()`/`.claim()` methods write to — so
`.subject(userId)` and `claims.put("sub", userId)` end up byte-for-byte identical in the
serialized token, and `claims.getSubject()` reads either one back correctly regardless of which
method set it. `role` isn't one of the registered claims — it's this app's own addition — so
there's no `.role(...)` method to reach for; the generic `.claim("role", role)` mechanism is the
*only* way to add it, by design, not an oversight.

**Takeaway**: prefer the dedicated setter for any registered claim (`.subject()` over a raw
`"sub"` map entry) — it's self-documenting and type-checked (`.subject(String)` can't silently
accept the wrong type or a typo'd key the way a `Map<String, Object>` entry can) — and reserve the
generic `.claim()`/`.claims()` calls for genuinely custom, app-specific claims with no registered
equivalent.

## JJWT validates `exp` during parsing itself — no manual expiry comparison needed, but its internal clock isn't the one this project already injects

**Context**: writing `JwtService`'s token-parsing method for `AUTH-001-AC-15` (expired session →
`401`), the question came up of whether expiry needs checking by hand against
`Instant.now(clock)` before trusting the claims.

**Why no manual check is needed**: `Jwts.parser()....parseSignedClaims(jwt)` validates the `exp`
claim against "now" as part of parsing itself, and throws `io.jsonwebtoken.ExpiredJwtException` if
it's already passed — before the payload is ever handed back. That exception extends
`ClaimJwtException`, which extends `JwtException`, so a single `catch (JwtException e)` (or
`@ExceptionHandler(JwtException.class)`) already covers it alongside signature/format failures,
with no extra code.

**The one nuance worth knowing**: the clock JJWT uses for that internal `exp` check is its *own*
`io.jsonwebtoken.Clock` interface — a completely different type from the `java.time.Clock` this
project already injects everywhere else (`ClockConfig`). It defaults to the real system clock
unless explicitly overridden via `.clock(...)` on the parser builder. There's no need to wire that
up, though: for a deterministic expired-token test, the simplest route is to mint the token using
`JwtService`'s *existing* `java.time.Clock` injection fixed to a point in the past, then parse it
normally with a real-clock parser — real "now" is already past that token's `exp`, so
`ExpiredJwtException` fires without ever touching JJWT's internal clock mechanism.

## `@Autowired`/`@Import` are no-ops on a plain Spock `Specification` with no Spring test context

**Context**: `JwtServiceSpec` (`AUTH-001-AC-12`) needed a real `JwtService` to inspect actual JWT
claims (`AuthControllerSpec` mocks `JwtService` entirely, so it never produces a real token to
decode). The spec had `@Import(ClockConfig)` on the class and `@Autowired Clock clock` as a field,
then built `new JwtService(clock, TEST_KEY, 15)` in `setup()` — `clock` was `null` at runtime,
NPE'ing inside `createJWT`'s `Instant.now(clock)`.

**Why**: `@Import`/`@Autowired` only do anything when a Spring test context actually gets
bootstrapped for that spec — via `@SpringBootTest`, a test-slice annotation (`@WebMvcTest`,
`@DataJpaTest`), or `@ContextConfiguration`. `JwtServiceSpec` had none of those, so Spock just ran it
as a bare POJO. Unlike a missing bean inside an *actual* Spring context (which fails loudly, as the
`@Value`-placeholder-resolution entries above show), `@Autowired` on a class with no container
involved at all doesn't error — it just leaves the field at its default (`null`), with nothing
enforcing it was ever going to be populated.

**Fix**: don't reach for Spring DI to test a plain class — construct what it needs directly, same as
this project's existing `Clock` convention already calls for in Spock specs:
```groovy
Clock clock = Clock.systemDefaultZone()
```
**General tell**: if a Spock spec has `@Autowired`/`@Import`/`@Value` but no `@SpringBootTest`-family
annotation anywhere on the class, those annotations are dead weight — either add the missing context
annotation, or (usually simpler, for a single collaborator like this) construct the real thing by
hand instead.

## Groovy's `as` can't coerce a `String` into a `SecretKey` — rebuild the key the same way it was signed

**Context**: same `JwtServiceSpec`, parsing the JWT back to inspect its claims used
`Jwts.parser().verifyWith(TEST_KEY as SecretKey)...`, where `TEST_KEY` is a plain Base64
`String`.

**Why**: Groovy's `as` coercion only works when there's an actual conversion path to the target
type (a constructor, an `asType`, or a built-in coercion) — `String` has no relationship to
`SecretKey` at all, so this throws `GroovyCastException` rather than producing a working key. It's
the same family of mistake as the "stub a `Role` field with a plain `String`" entry above: Groovy
happily lets you *write* the cast, but there's nothing behind it at runtime.

**Fix**: build the key exactly the way `JwtService` itself does when signing — decode the Base64
string into bytes and wrap it:
```groovy
Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_KEY))
```
Verifying/parsing a signed token needs the identical key *material and shape* used to sign it —
there's no shortcut coercion from the raw secret string.

## Minting the JWT signing secret has three separate, independent footguns — fixing one doesn't fix the others

**Context**: getting `app.jwt.secret`'s local-dev default right in `application.yaml` took three
separate review rounds, each surfacing a different failure mode even though the previous one had
genuinely been fixed.

1. **Invalid Base64 characters.** `Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))` uses the
   *standard* Base64 decoder, whose alphabet is `A-Za-z0-9+/` only. A human-typed placeholder like
   `jwt-secret-key` has hyphens in it, which aren't in that alphabet — `decode(...)` throws
   `IllegalArgumentException` immediately, before the key length even comes into question.
2. **Decoded byte length, not encoded string length, has to clear HS256's floor.** Even once the
   characters are all valid Base64, the *decoded* byte count is what matters: HS256 needs a
   minimum 256-bit (32-byte) key, and roughly 4 Base64 characters decode to 3 raw bytes — so a
   36-character valid-looking Base64 string only yields 27 bytes (216 bits), still short enough to
   trip `Keys.hmacShaKeyFor`'s `WeakKeyException`. Need ~44+ Base64 characters for a real 256-bit
   key.
3. **The env var name itself can't have hyphens either.** `JWT-SECRET-KEY` isn't a valid POSIX
   environment variable name (shell identifiers are `[A-Za-z0-9_]` only) — a literal
   `export JWT-SECRET-KEY=...` is a shell syntax error. Needs underscores, matching this project's
   own existing `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` convention.

**Takeaway**: a secret that "looks like" a reasonable placeholder at a glance can fail for any of
these three unrelated reasons — worth generating a real one with a tool (`openssl rand -base64 32`)
rather than hand-typing a string, both to sidestep all three at once and because a real secret
shouldn't be composed by eye in the first place.

## Logging is already on the classpath transitively — code against `org.slf4j.Logger`, not `java.util.logging`

**Context**: came up while fixing `AUTH-001-AC-11` (the `GlobalExceptionHandler` was forwarding
`LoginException`'s specific message — "user doesn't exist" vs "wrong password" — straight to the
client; the fix needed the *reason* logged server-side instead of returned in the response body).

**Why no new dependency was needed**: every `spring-boot-starter-*` in `build.gradle.kts`
(`-webmvc`, `-data-jpa`, `-security`, etc.) transitively pulls in `spring-boot-starter-logging`,
which bundles both the SLF4J API and Logback as its binding. Checked `build.gradle.kts` for any
exclusion of it — none — so it's present with zero lines added.

**Why `org.slf4j.Logger`, not `java.util.logging.Logger` or `ch.qos.logback.classic.Logger`
directly**: Logback implements the SLF4J API rather than exposing its own public API for app code
to call — SLF4J is the facade app code is meant to hold, Logback is just the backend Spring Boot
wires in underneath. `java.util.logging` is the JDK's unrelated built-in logger; nothing in a
Spring Boot app expects to see it.

**Pattern**:
```java
private static final Logger log = LoggerFactory.getLogger(AuthService.class);
...
log.warn("Login failed for {}: {}", email, "wrong password"); // .warn, not .error — a failed login is routine, expected traffic, not an application fault
```
Never log the raw password itself, even on a mismatch.

## A "uniform" failure response can still leak information through timing, even with identical status/body

**Context**: code review of `AuthService.login` (`AUTH-001-AC-11`) after the `AuthServiceDataJpaSpec`
gaps were closed. The method short-circuits with `LoginException` the moment `findByEmail` comes
back empty, but calls `passwordEncoder.matches(...)` (BCrypt) before throwing when the email exists
but the password doesn't match.

**Why this is a real gap despite every test passing**: AC-11's whole point is that the `401`
response must not reveal *which* of "no such email" vs "wrong password" happened. Every test that
exists (`AuthControllerSpec`, `AuthServiceSpec`, `AuthServiceDataJpaSpec`) only asserts the
response's status/body/cookies — all identical either way, so they all pass. But BCrypt is
deliberately slow (~100ms, by design, to resist brute-forcing), and only the wrong-password branch
pays that cost; the no-such-email branch returns almost immediately. That timing gap is itself a
side channel an attacker can use to enumerate registered emails, independent of the response being
byte-for-byte identical. None of the three test layers could ever have caught this — they're all
response-shape assertions, and this is a response-*time* property.

**Fix / general takeaway**: when an AC requires a uniform response across different failure
reasons, treat "uniform" as including wall-clock cost, not just status code and body — any
early-return branch that *skips* an expensive operation (password hashing, crypto comparisons,
anything deliberately slow) the other branch pays is a candidate. The standard mitigation is to
always perform the expensive comparison — e.g. `passwordEncoder.matches(password, DUMMY_HASH)`
against a constant precomputed hash — even on the branch that's going to reject regardless of the
result, so both paths cost the same regardless of which one is actually taken.

**A dummy hash has to actually look like a real one, or the mitigation silently does nothing**:
implementing the fix above, `DUMMY_HASH` was first set to a password-shaped but wrong-length
string. `BCryptPasswordEncoder.matches()` checks the *shape* of the encoded value with a regex
(`$2a$10$` + exactly 53 further BCrypt-base64 characters) **before** doing any real hashing — if
that check fails, it logs `WARN ... Encoded password does not look like BCrypt` and returns
`false` immediately, skipping the actual (deliberately slow) comparison entirely. So a
plausible-looking but malformed dummy hash makes the "constant-time" branch near-instant —
defeating the mitigation while every functional test still passes, since they only assert
`LoginException` was thrown, never the timing. Only caught here by deliberately re-running with
`--info` logging and spotting the `WARN`. Fix: the dummy hash must be a real, well-formed BCrypt
output — generate one once via `new BCryptPasswordEncoder().encode(...)` and hardcode the result,
rather than hand-typing something merely hash-*shaped*.

## A Spock `Spy` to verify an interaction on a real collaborator is usually a sign the check belongs somewhere else

**Context**: `AuthServiceDataJpaSpec`'s AC-11 test tried asserting `1 * passwordEncoder.matches(...)`
to prove the dummy-hash mitigation above actually fires — but that spec constructs
`passwordEncoder` with `new BCryptPasswordEncoder()`, a real instance. Spock's interaction syntax
only works against objects it's tracking (`Mock()`/`Stub()`/`Spy()`); on a plain `new`-constructed
object it reports `0 invocations`, even though the real call demonstrably happens.

**Why reaching for `Spy()` here wasn't the right fix**: Spock's own docs carry an explicit warning
on `Spy` — "think twice before using this feature; it might be better to change the design of the
code under specification." The smell it's pointing at: needing to spy on a real collaborator to
verify a call happened usually means the test is in the wrong place, not that the test needs a
heavier tool. `AuthServiceSpec` (a plain unit spec, no DB) already declares
`PasswordEncoder mockPasswordEncoder = Mock()` for this exact collaborator, and already has the
matching "login fails for unknown user" scenario — a plain `Mock()`'s interaction verification just
works there, no `Spy` required.

**Fix / general tell**: before reaching for `Spy()` to verify an interaction, check whether a
sibling spec already mocks the exact same collaborator for a unit-level version of the same
scenario — the interaction assertion almost always belongs there, leaving the
`@DataJpaTest`/real-object spec to assert only the things that genuinely need the real
implementation (e.g. real DB state).

## The "uniform response regardless of cause" principle isn't login-specific — it recurs anywhere one endpoint can fail for several different reasons

**Context**: deciding what `GlobalExceptionHandler`'s planned `JwtException`/`JwtParserException`
handler should return for `/me`'s session-validation failures (`AUTH-001-AC-15`) — expired,
malformed, bad signature, missing subject, or (once added) a valid token naming a now-deleted user
all currently produce exceptions with distinct, descriptive messages.

**Why that's the same decision as `AUTH-001-AC-11` all over again**: `AuthService.login` already
collapses "no such email" and "wrong password" into one identical `401`/message specifically so
the response itself can't be used to tell the two apart. The same reasoning applies here —
returning each JWT failure's real exception message would tell a caller whether their token's
signature actually validated (just expired) versus was rejected outright, a meaningful signal to
anyone probing a forged or stolen token, not just internal implementation detail. It's also the
same category of leak already fixed once in this file's catch-all `Exception` handler, which used
to return `ex.getMessage()` directly — third-party/library exception text is never written with
client consumption in mind.

**Takeaway**: whenever a new multi-cause failure path shows up (this is now the second one in this
codebase), default to one fixed, generic message for the whole family of causes, log the real
exception server-side only, and treat "has this AC's response already hidden its real cause?" as a
standing question for every new `@ExceptionHandler`, not something to re-derive from scratch each
time.

## Spring Data repositories only ever throw `DataAccessException` subtypes — catch narrowly, not `Exception`

**Context**: came up deciding what, if anything, to wrap around `userRepository.saveAndFlush(...)`
and other repository calls in `AuthService`, beyond the existing `DataIntegrityViolationException`
catch for duplicate emails.

**Why `DataAccessException`, not raw JDBC/Hibernate exceptions**: Spring Data repositories are
`@Repository`-annotated, so `PersistenceExceptionTranslationPostProcessor` already translates every
low-level JDBC/Hibernate failure into Spring's own `DataAccessException` hierarchy before it ever
reaches application code — a raw `SQLException` never surfaces here.

**Why not just catch `Exception` everywhere and be done with it**: it also catches bugs that have
nothing to do with the database (an NPE from unrelated code in the same method) and silently
reinterprets them as "a DB problem" — exactly the kind of masking that makes a real bug harder to
find later (this is also why SonarQube flags broad `Exception`/`Throwable` catches).

**The actual dividing line**: only catch narrowly where the exception maps to a *specific
client-facing outcome* — `DataIntegrityViolationException` → `409` for a unique-constraint hit
(duplicate email) is exactly that; `OptimisticLockingFailureException` would be the equivalent if
`User` ever gets `@Version`-based optimistic locking. Infra-level failures
(`DataAccessResourceFailureException`, `CannotGetJdbcConnectionException`, `QueryTimeoutException`
— DB unreachable, connection pool exhausted) aren't worth catching per call, because there's
nothing more specific to do with them than a generic `500` anyway. Handle those with one catch-all
`@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler` instead of scattering speculative
`try`/`catch` through every repository call.

## A derived repository method name (`findByXAndY`) has no implementation anywhere — Spring parses it into a query at startup

**Context**: came up explaining `AccountRepository.findByUserId(UUID)` — there's no method body
anywhere, and no hand-written SQL, yet it works.

**Why**: Spring Boot never generates a hand-written implementation for a `JpaRepository` subinterface
— it builds a dynamic proxy backed by `SimpleJpaRepository`. For a non-CRUD method name, that proxy
runs the method name through `PartTree` *once, at application startup* (not per call): it strips the
`findBy`/`getBy` prefix, splits what's left on `And`/`Or` into parts (`findByUserId` → `[UserId]`,
`findByUserIdAndType` → `[UserId, Type]`), and resolves each part against the entity's actual
properties via reflection — including walking nested paths (`findByUser_Email` or `findByUserEmail`
if the entity has a `User user` with an `email` field). From that parsed structure it synthesizes a
real JPQL query (effectively `SELECT a FROM Account a WHERE a.userId = :userId`), cached from then on
— the method name is a compile-time-checked DSL for a query, not a label on hand-written logic.

**The useful side effect**: an unparseable name (typo'd property, unsupported keyword) fails at
*startup* with `PropertyReferenceException`, not as a runtime 500 later — broken derived queries are
caught before the app even finishes booting.

**When the naming convention stops being enough**: `@Query` (JPQL or `nativeQuery = true`) for
anything the name-parser can't express cleanly — joins, aggregations, subqueries — or
`Specification`/Querydsl for predicates composed dynamically at runtime rather than fixed at compile
time. This project's own convention (`structure.md`) is to avoid `@Query` unless there's a real
complexity/scale reason, which lines up with the tradeoff above: derived queries are both cheaper to
read and fail louder when wrong, so they're the right default until a query needs expressiveness the
name-parser genuinely can't give it.

## Pairing an indexed column with a non-indexed one in a query doesn't lose the index — it narrows via the index, then filters the rest

**Context**: came up reasoning about `findByIdAndUserId`-shaped queries on `Account` — `id` is the
PK (indexed), `userId` isn't. Question was whether combining them in one query loses the benefit of
the PK index.

**Why it doesn't**: Postgres's planner uses the indexed column to narrow down to candidate rows
first (`Index Cond` in an `EXPLAIN` plan), then evaluates any non-indexed predicate as a `Filter`
applied only to whatever rows the index step already returned — not a second table scan. The filter
step's cost scales with how many rows survived the index narrowing, not with table size and not with
whether that second column has an index. For a unique PK, the index narrows to at most one row, so
filtering that one row by `userId` is free regardless.

**Where it would actually matter**: if the *indexed* column isn't very selective on its own (e.g. an
index on `account_type`, which could match thousands of rows), pairing it with a non-indexed filter
means fetching all those candidates just to discard most of them. That's the case a composite index
(`(account_type, user_id)`) earns its keep — it pushes both conditions into `Index Cond` instead of
leaving the second one as a post-fetch `Filter`.

**Takeaway**: the question to ask isn't "is every column in my WHERE clause indexed" — it's "how
selective is the indexed column by itself." Verify the actual plan rather than reasoning about it in
the abstract: `EXPLAIN ANALYZE SELECT ...` labels each predicate as `Index Cond` or `Filter`
directly.

## Adding a catch-all `@ExceptionHandler` silently removes Spring's own unhandled-exception logging — log it yourself

**Context**: after adding `@ExceptionHandler(Exception.class)` to `GlobalExceptionHandler` (per the
entry above), the question came up of whether anything still logs the real exception, or whether
the handler needs to do it itself.

**Why nothing logs it for free**: once a `@RestControllerAdvice` method resolves an exception by
returning a response, Spring MVC treats it as fully handled — it never falls through to the
default unhandled-exception path (the `WARN`-level logging `DispatcherServlet`/the default error
controller would otherwise do). From the framework's point of view nothing went wrong: a method
caught the exception and produced a `200`-shaped response for it (just with a `500` status). Adding
the catch-all is what makes that happen — and it silently removes the only logging that exception
used to get.

**Fix**: log explicitly inside the handler, passing the `Throwable` itself (not just
`ex.getMessage()`) so the stack trace actually prints:
```java
LOG.error("Unhandled exception", ex);
```
Use `.error()` here specifically — this bucket is genuinely unexpected faults, unlike the `.warn()`
calls used for routine failed-login/duplicate-email outcomes in the entry above.

**Confirmed again on a non-catch-all handler**: adding a dedicated
`@ExceptionHandler(JwtException.class)` (for `/me`'s session-validation failures) hits the
identical silence — it's not specific to the catch-all `Exception` handler; *any*
`@ExceptionHandler` method that resolves an exception into a response removes Spring's default
logging for it. This codebase's actual convention, visible in `LoginException`/
`DuplicateEmailException` (never logged in `GlobalExceptionHandler` at all), is to log once, at
the point of detection in the service/component that throws — e.g. `JwtService` logging a `WARN`
right before/as it lets a JWT failure propagate — and keep `GlobalExceptionHandler` itself logging
only the true catch-all case, where there was no earlier opportunity to log.

## Not every SonarQube/SonarLint rule honors `@SuppressWarnings` — some only resolve via the IDE's own "mark as" action

**Context**: added `@SuppressWarnings("java:S2143")` above `JwtBuilder.createJWT` to suppress a
"use the java.time API" finding that's a legitimate false positive here (JJWT 0.12.x's
`issuedAt`/`expiration` methods only accept `java.util.Date` — there's no `Instant` overload to
switch to). The warning kept showing in the IDE afterward.

**Two separate things worth checking before assuming the annotation itself is wrong**: first,
whether the file actually compiles yet — a broken file (there was also a `signWith` compile error
in play at the same time) can leave a static analyzer showing stale results from before the fix.
Second, and more fundamentally: SonarLint/SonarQube's Java analyzer only honors `@SuppressWarnings`
for a limited subset of rules — others are structural or security-hotspot-style findings that can
*only* be resolved by explicitly marking them "Won't Fix" / "False Positive" through the IDE's own
SonarLint panel or the SonarQube UI, never through a code annotation. If a clean, compiling file
still shows a finding after a correctly-formatted suppression, that's the more likely explanation
than a formatting mistake.

## IntelliJ's "Method may be static" is a false positive on a Spock helper that stubs interactions

**Context**: `AuthServiceSpec`'s private `mockUserResponse(User mockUser, UUID id) { mockUser.getEmail()
>> EMAIL; ... }` helper (shared by the registration-success and login-success tests) got flagged by
IntelliJ as "may be static." Making it `static` broke every test that calls it, with a runtime
error: `Interactions cannot be declared in static scope.`

**Why the inspection gets this wrong**: IntelliJ's check only looks at whether a method body reads
or writes a field declared on the enclosing class. `mockUserResponse` only touches its own
parameters, so the inspection concludes it needs no `this` and offers to make it `static`. But a
`>>` line isn't ordinary Groovy — Spock's compile-time AST transform rewrites it into a call that
registers the stub against the *currently executing feature's* interaction-recording context
(`SpecificationContext`), which lives on the `Specification` instance. That's real instance-state
access IntelliJ's field-usage scan can't see, because it only exists after Spock's transform runs.
A `static` method has no `this` at all, so there's nothing for Spock to attach the interaction to.

**Fix**: don't make it static. Clear the warning with IntelliJ's own quick-fix instead — place the
cursor on the method, `Alt+Enter` on the warning, choose "Suppress for method." That inserts the
correct Groovy-inspection suppression annotation above just that method, leaving the inspection
active for everything else in the file. Same category as the `JwtService`/SonarLint entry above —
a framework-specific blind spot in static analysis, worth suppressing locally once confirmed to be
a false positive rather than disabling the inspection project-wide.

## A non-static Groovy nested class breaks IntelliJ's map-constructor resolution, even though it works fine at actual Groovy runtime

**Context**: a test helper added a nested `class AuthenticatedUser { UUID id; String jwt }` inside
`AccountControllerSpec`, with a helper method returning `new AuthenticatedUser(id: ..., jwt: ...)`.
IntelliJ flagged that line: "Cannot apply default constructor for class 'AuthenticatedUser'."
Temporarily wiring the helper into a real test and running it passed cleanly — no error at all.

**Why**: a non-static nested class carries an implicit reference to its enclosing instance (same
as Java), so its real constructor takes that outer instance as a hidden parameter — there's no
true no-arg constructor for Groovy's map-style construction sugar (`new X(a: 1, b: 2)`, itself
built on top of a genuine no-arg constructor plus property setters) to target. At runtime this
still works, because the helper method is itself an instance method of the enclosing
`AccountControllerSpec`, so Groovy's dynamic dispatch can supply the implicit outer reference
automatically when constructing the inner class. IntelliJ's static Groovy inspector doesn't model
that resolution path for the named-argument constructor form, so it warns about something that's
actually fine — another instance of an IDE/Groovy-runtime blind spot, same family as the
SonarLint-suppression and "method may be static" entries above.

**Fix**: make the nested class `static` whenever it doesn't actually need the enclosing instance
(a pure data holder, as here) — it gets a genuine no-arg constructor, the warning disappears, and
it stops silently carrying a reference to the whole enclosing `Specification` (and everything that
drags in — `MockMvc`, mocked Spring beans, etc.) for no reason.

## A field annotated `@Mock` isn't a Spock mock unless it's also created via `Mock()`/`Stub()`

**Context**: `JwtAuthenticationFilterSpec` declared `@Mock MockFilterChain chain = new MockFilterChain()`
and then asserted `1 * chain.doFilter(request, response)` in a `then:` block. The real call
happened (confirmed via the production code), but the test failed with `Too few invocations for:
1 * chain.doFilter(request, response) (0 invocations)`.

**Why**: the `@Mock` in play was `org.mockito.Mock`, not a Spock annotation, and this project's
specs run under Spock's own runner — no `MockitoJUnitRunner`/`MockitoExtension` anywhere in the
mix — so that annotation is never processed; it's inert decoration. The field itself was
initialized as a real, concrete `MockFilterChain` instance, not something built through Spock's
`Mock()`/`Stub()` factory methods. Spock can only record `N * obj.method(...)`-style interactions
against objects it created as doubles — a genuine concrete object is invisible to its interaction
bookkeeping no matter how many times its methods are actually called, so the count comes back zero.

**Fix**: use a real Spock mock of the relevant interface — `FilterChain chain = Mock(FilterChain)`
(the `jakarta.servlet.FilterChain` interface the production code depends on, not the concrete
`MockFilterChain` class) — and drop the unused `org.mockito.Mock` import entirely. **General
tell**: if `N * obj.method(...)` reports zero invocations despite the method clearly having run,
check whether `obj` was actually created via Spock's `Mock()`/`Stub()`/`Spy()`, not just annotated
or assigned a real instance.

## `SecurityContextHolder.clearContext()` leaves `.context.authentication` as `null`, not a "not authenticated" object

**Context**: `JwtAuthenticationFilterSpec`'s "no cookie present" test asserted
`!SecurityContextHolder.context.authentication.authenticated` after `clearContext()` and expected
a clean `false`. Accessing `.authentication` after the clear returned `null` instead, which would
NPE on the `.authenticated` navigation (Groovy's `!null.foo` throws, it doesn't evaluate to `true`).

**Why**: `clearContext()` removes the context entirely; the next `getContext()` call lazily
creates a brand-new, empty `SecurityContextImpl`, and an empty context's `authentication` property
genuinely is `null` — Spring Security doesn't synthesize a stub "nobody's logged in" `Authentication`
object at this level. In a real running app, `AnonymousAuthenticationFilter` (further down the
chain) populates the context with an anonymous `Authentication` that actually reports
`authenticated == true` — but a filter spec that drives `JwtAuthenticationFilter` directly, in
isolation, never runs that filter, so there's nothing to produce anything but `null` when no cookie
is present.

**Fix**: assert `authentication == null` directly rather than navigating into a `.authenticated`
property that may not exist. Don't expect the same assertion shape to work both for "cookie
present but context cleared mid-test" (AC-20/21/22's cleanup) and "nothing ever authenticated this
request" (AC-21/22's actual check) — they're different states.

## Spock `where:` block cells are evaluated once, up front — they can't see `given:` locals or rely on `setup()` having run yet

**Context**: `JwtAuthenticationFilterSpec`'s AC-22 (`@Unroll`, data-driven) built JWTs for its
`where:` table via `jwtHelper.buildExpiredJwt(subject, ROLE_USER)`, where both `jwtHelper` and
`subject` were plain local variables declared inside that same feature's `given:` block. Running it
threw `groovy.lang.MissingPropertyException: No such property: jwtHelper for class:
JwtAuthenticationFilterSpec` — Groovy fell back to looking for a class-level property and found
none.

**Why**: a `where:` block isn't just another part of the feature method body — Spock's AST
transform has to evaluate its cell expressions *before* any iteration's `given:`/`when:`/`then:`
runs at all, because it needs the full data table up front to know how many iterations to generate.
At that point in the lifecycle, nothing declared inside `given:` exists yet. `where:` cells can only
reference: previously-computed columns in the same table (left-to-right), or fields already present
on the specification instance — and per Spock's docs, those fields have to be `@Shared` (or have a
value from a field initializer/`setupSpec()`), because a plain instance field's `setup()`-assigned
value likewise hasn't been set yet at `where:`-evaluation time either.

**Fix**: promote anything a `where:` table needs to a `@Shared` field with its value coming from a
field initializer, mirroring this spec's existing `@Shared JwtHelper jwtHelper = new JwtHelper()` —
not from `setup()`.

**Still-open follow-up caught in review, not yet fixed**: `JwtAuthenticationFilterSpec` also has
`@Shared String randomUuid` whose *value* is assigned inside instance `setup()`, not a field
initializer. That half-fixes the visibility problem (the field exists, so `where:` can see it) but
not the timing problem — the UUID baked into the `where:`-table-built JWTs is whatever `setup()`
last left `randomUuid` as from the *previous* feature method's run, not a value freshly generated
for this feature. Harmless today only because AC-22 never asserts anything about the token's
subject matching `randomUuid`; running AC-22 in isolation (no prior feature's `setup()` having
run yet) would bake in `null` instead, meaning the "expired"/"invalid-signature" rows could end up
silently testing the "missing subject" code path instead of the one the test name claims. Give
`randomUuid` a `@Shared` field initializer (`UUID.randomUUID().toString()`) too, same as
`jwtHelper`, rather than reassigning it in `setup()`.

## `addFilterBefore(filter, SomeFilter.class)` doesn't require `SomeFilter` to actually exist in the chain

**Context**: a test asserted `JwtAuthenticationFilter` runs before an actual
`UsernamePasswordAuthenticationFilter` instance in the built `SecurityFilterChain`. Printing the
real chain showed 12 filters — no `UsernamePasswordAuthenticationFilter` anywhere in it at all.

**Why**: `SecurityConfig` calls `.addFilterBefore(jwtAuthenticationFilter,
UsernamePasswordAuthenticationFilter.class)`, but that `.class` argument is only a **position
anchor** — Spring Security maintains a fixed, known ordering of filter slots, and `addFilterBefore`
places the given filter instance immediately ahead of wherever that class *would* sit, without
requiring (or causing) an instance of it to exist. `UsernamePasswordAuthenticationFilter` is only
ever actually constructed when something calls `.formLogin()` (or explicitly registers it); this
app authenticates via a custom JSON login (`AuthController`/`AuthService`), never form login, so
that filter genuinely never exists in any chain this app builds — there's nothing to compare
against, ever, regardless of how the test is written.

**Fix**: assert position against a filter that's actually guaranteed to be present and whose
relative ordering genuinely matters functionally — e.g. `AuthorizationFilter`, since
`JwtAuthenticationFilter` populating `SecurityContextHolder` before the authorization decision runs
is the real invariant worth protecting, not literal "before the username/password filter." Revise
the spec's AC wording to match once the test changes, so the requirement and the test stay in sync.

## Filter-chain-order tests belong in a context-loading sibling spec, not the filter's own unit spec

**Context**: `JwtAuthenticationFilterSpec` originally held a skeleton for "`SecurityConfig`
registers `JwtAuthenticationFilter` ahead of X" — but that assertion is about `SecurityConfig`'s
wiring, not `JwtAuthenticationFilter`'s own behavior, and verifying it needs a real, built
`SecurityFilterChain` (a `List<Filter>` only a Spring context actually assembles), which has nothing
to do with the rest of that spec's fixture (`JwtService`, `JwtHelper`, mock request/response/chain).

**Why**: this project's "one spec per class under test" convention means the subject here is
`SecurityConfig`, so the test belongs with `SecurityConfig`'s other tests — but the existing
`SecurityConfigSpec` is a plain `new SecurityConfig()` unit spec (for its `passwordEncoder()` bean
method), which can't produce a real `SecurityFilterChain` either. This is the same shape of problem
`AuthServiceSpec`/`AuthServiceDataJpaSpec` already solved: same class under test, split across two
specs by *infrastructure need* (plain POJO vs. a real Spring context) rather than one spec carrying
mixed setup.

**Fix**: add a new sibling spec rather than complicating the existing one, named after whichever
Spring test slice it needs — `SecurityConfigSpringBootSpec` here, using `@SpringBootTest` plus
`@ActiveProfiles("test")` and `@Autowired SecurityFilterChain securityFilterChain`, then
`.filters.findIndexOf { it instanceof X }` to compare real positions. Keep `SecurityConfigSpec`
itself as the plain-POJO spec for anything not requiring a built chain.

## `@SpringBean` replaces a bean everywhere in the context — including inside another bean's constructor args, not just at the call sites you wrote

**Context**: `AuthControllerSpec` (`@WebMvcTest(controllers = AuthController)` + `@Import(SecurityConfig)`)
declares `@SpringBean JwtService mockJwtService = Mock()`. AC-14's test separately built a second,
real `JwtService` instance by hand (`new JwtService(Clock.systemDefaultZone(), TEST_KEY, 15)`) just
to mint a JWT string for the cookie. At request time, `JwtAuthenticationFilter.getClaim(...)` came
back `null` — looked like a signing-key mismatch between the "test" key and some "real" key, but
wasn't.

**Why**: `@SpringBean` doesn't just make `mockJwtService` available for you to call directly — it
replaces the `JwtService` bean definition for the *entire* Spring context this test boots, including
everywhere else that bean gets injected. `SecurityConfig.securityFilterChain(..., JwtService
jwtService)` takes `JwtService` as a constructor-style parameter and bakes whatever gets injected
straight into `new JwtAuthenticationFilter(jwtService)` — so the filter actually running on every
request in this test is holding the *mock*, never anything built from a real secret. The real
`JwtService` instance created locally in `given:` is a decoy: it's only ever used to produce a
string for the cookie, and is never wired into the filter that reads that cookie back.  Since
`getClaim` was never stubbed, Spock's `Mock()` returned its default for an unstubbed method with an
object return type — `null` — which was the actual symptom. A genuine key mismatch would have
thrown `JwtParserException` instead (caught by the filter's `try/catch`), never silently returned
`null`; that `getClaim` has no code path returning `null` on its own is itself a tell that an
unstubbed mock, not a real parsing failure, is in play.

**Consequence worth acting on, not just explaining**: once `JwtService` is mocked for a test, the
actual *content* of any JWT string in play is irrelevant — `getClaim` never really parses it.
Building cryptographically real malformed/expired/invalid-signature tokens via `JwtHelper` (as
`JwtAuthenticationFilterSpec` correctly does, where `JwtService` is real) is unnecessary work one
layer up in `AuthControllerSpec` — any opaque string works for the cookie value, and the actual
pass/fail behavior comes entirely from how `mockJwtService.getClaim(...)` is stubbed (return a
`JwtClaim`, or throw `JwtParserException`).

## Groovy `def` inside an `if`/`else` branch doesn't leak out to the rest of the method

**Context**: a `when:` block assigned `mockMvc.perform(...)`'s result inside both branches of an
`if (cookie == null) { ... } else { ... }`, using `def result = ...` in each branch. The following
`then:` block's `result.andExpect(...)` threw `groovy.lang.MissingPropertyException: No such
property: result`.

**Why**: `def` (or any typed declaration) inside an `if`/`else` block scopes that variable to the
block itself — it ceases to exist once the block ends, same as Java. By the time `then:` runs,
there's no `result` variable anywhere in the method's scope, so Groovy interprets the bare
identifier as a property-access attempt on the spec instance, finds nothing, and throws rather than
silently returning `null`.

**Fix**: declare the variable once above the conditional (`def result`), then assign (without `def`)
inside each branch, so the same variable reference is still in scope when `then:` reads it.

## Spock only auto-invokes a mock-response closure written literally at the `>>` call site — not one stored in a variable

**Context**: a data-driven test tried to vary a stub's *response* per `where:` row by putting
closures directly in the table — `mockJwtService.getClaim(_ as String) >> getClaimResult`, where
`getClaimResult` came from a `where:` column holding values like `{ new JwtClaim(...) }` and
`{ throw new JwtParserException("bad token") }`. Rows that needed the "throw" closure to actually
fire failed with `GroovyCastException: Cannot cast object '...closure4@...' ... to class
'JwtClaim'`, thrown from Spock's own `ConstantResponseGenerator.doRespond`.

**Why**: Spock's "this closure is a response generator — call it per invocation, instead of
returning it as a value" behavior is detected by its compile-time AST transform recognizing a
closure written *literally* immediately after `>>` at the interaction declaration. Once the closure
is held in a variable instead — even one bound from a `where:` table — that syntactic pattern is
gone by the time the transform runs, so Spock falls back to `ConstantResponseGenerator`: it treats
the variable's value as a plain constant to hand back as-is. Since `JwtService.getClaim`'s declared
return type is `JwtClaim`, it then tries to coerce that raw `Closure` object into a `JwtClaim` and
fails. (The row that didn't need the throw to fire — "no cookie" — never triggered this at all,
because `JwtAuthenticationFilter` short-circuits on a null `Cookie[]` and never calls `getClaim` in
that branch in the first place; that row only got as far as the separate `def`-scoping bug above.)

**Fix**: write the closure literally at the stub call site, pulling only a plain (non-closure) value
out of the `where:` table for it to reference:
```groovy
given:
if (cookie != null) {
    mockJwtService.getClaim(_ as String) >> { throw new JwtParserException(exceptionMessage) }
}

where:
scenario           | cookie       | exceptionMessage
"malformed cookie" | "bad cookie" | "bad token"
```
Also worth dropping a `where:` row entirely rather than stubbing a response for it, if the code path
being tested never actually reaches the mocked call on that row (as above, with "no cookie").

## Test-only constants added to a main-source-set `Constants` class ship in the production JAR with zero production usages

**Context**: `Constants.java` (`backend/src/main`) picked up `ROLE_USER`, `ROLE_ADMIN`, and
`TEST_KEY` — all added purely so Spock specs had somewhere to import a shared role string / signing
key from. A pre-commit review grepped `backend/src/main` for any of the three and found none —
every reference was from `src/test`.

**Why this matters, not just "slightly untidy"**: a main-source `Constants` class is part of what
ships in the production JAR. Test-only values living there have no compiler-enforced link to
anything real (there's already a genuine `Role` enum, `model/Role.java`, with `USER`/`ADMIN` —
`ROLE_USER`/`ROLE_ADMIN` as plain strings duplicate it without that enum ever being able to catch a
drift between the two), and `TEST_KEY` being physically reachable from production code, even though
nothing calls it, is the kind of thing a future secret-scanning or security-review pass flags for no
reason.

**Fix**: a dedicated test-source-set class — `TestConstants` under
`backend/src/test/groovy/.../common/` — for anything that exists purely to give specs a shared
literal to import. **General tell during review**: when a "constants" file picks up a new value
mid-feature, grep the main source set for actual usages before assuming it belongs there; "used
throughout testing" written as a comment on a main-source constant is itself a signal it's in the
wrong source set.

## Reusing an AC label for a new test doesn't make it test that AC — check what the label already means first

**Context**: six tests across three specs (`JwtServiceSpec`, `AuthServiceSpec`,
`AuthServiceDataJpaSpec`) were labeled `AUTH-001-AC-14` while being hand-coded alongside the actual
AC-14 work. A pre-commit review caught that `AUTH-001-AC-14` already has one specific, established
meaning in `backend_spec_001_basic_login.md` — "`GET /api/auth/me` with a valid cookie returns `200`
with the user body" — which `AuthControllerSpec` already covers correctly. The six mislabeled tests
were testing something else entirely: `JwtService.getClaim`'s own parsing contract, and
`AuthService.getUserDetailsById`'s found/not-found behavior — real, worthwhile collaborator-level
tests that just didn't have their own AC to point to yet.

**Why**: it's natural, while implementing one AC, to lean on its number for every test written in
service of it — but an AC label is supposed to be a 1:1 pointer to one specific, already-written
requirement statement, not a loose "this is part of the same effort" tag. Once reused loosely, the
AC's own meaning gets muddied for anyone using the label to find out what's actually guaranteed, and
the collaborator-level behavior being tested has no requirement of its own on record at all.

**Fix**: when a new test's behavior doesn't match an existing AC's statement word-for-word, don't
borrow that AC's number — write a new AC for it instead (this spec already has a precedent for ACs
discovered mid-implementation: Requirement 6 for email normalization, Requirement 7 for the JWT
filter, and now Requirement 8 for these two collaborator contracts, `AUTH-001-AC-24`/`AC-25`).
**General tell during review**: grep the spec for every AC label a new/changed test file uses, and
confirm each one's text still actually describes what that specific test asserts — not just that
the number existed and was topically nearby.

## Never add a `@SpringBean` mock for the class a `@WebMvcTest(controllers = ...)` is targeting

**Context**: while first drafting the logout test, `AuthControllerSpec` briefly gained
`@SpringBean AuthController mockAuthController = Mock()` alongside the existing
`mockAuthService`/`mockJwtService` doubles, with interactions like `1 * mockAuthController.logout()`
meant to verify "the response clears the cookie."

**Why**: `@WebMvcTest(controllers = AuthController)` exists specifically to stand up a *real*
`AuthController` bean wired behind `MockMvc` — that's the entire reason to name a controller in
`controllers = ...`. Replacing that bean with a Spock `Mock()` via `@SpringBean` doesn't just break
the one new test: it silently breaks *every* test method in the whole spec class, since
`mockMvc.perform(...)` now routes every request the file sends to a mock with none of the
controller's real logic. `mockAuthService`/`mockJwtService` are correctly mocked because they're the
controller's *collaborators*, injected as constructor dependencies — the controller is the one thing
this kind of test exists to exercise for real, and should never itself be the thing you mock.

**Fix**: never add a `@SpringBean` double for whatever class is named in a `@WebMvcTest`'s
`controllers = ...`. To verify a response detail like "the cookie got cleared," inspect the actual
HTTP response (`MockMvcResultMatchers.cookie()...`) rather than trying to verify an internal method
call on the controller — you don't have access to (and don't need) call verification on the subject
under test itself.

## `ResponseEntity.BodyBuilder` needs a terminal `.build()`/`.body(...)` — returning the builder itself doesn't typecheck as the `ResponseEntity` you want

**Context**: a `ResponseEntity<Void> logout()` method tried to return
`ResponseEntity.status(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, cookie.toString())` directly.

**Why**: `.header(...)` returns the same `ResponseEntity.BodyBuilder` it was called on, not a built
`ResponseEntity` — the fluent chain needs an explicit terminal call. `register`/`login` already have
a body to attach, so they terminate with `.body(response)`; a response with no body needs the other
terminal method `BodyBuilder` provides for exactly that case.

**Fix**: `.header(...).build()` — `.build()` is the status-and-headers-only terminator, producing a
genuine `ResponseEntity<Void>`.

## A MockMvc test can't make "the cookie left after logout" differ from "no cookie" — and shouldn't try to

**Context**: writing `AUTH-001-AC-17` ("the session is unusable immediately after logout") raised
the question of whether the test needed to literally replay the cookie value from logout's
`Set-Cookie` response on the following `/me` request.

**Why**: logout's cookie-clear sets `Max-Age=0`, which is exactly the signal a real browser uses to
delete a cookie outright and never resend it — so "the cookie present on the client immediately
after logout," from the next request's perspective, *is* no cookie at all. MockMvc doesn't model a
persistent browser cookie jar across separate `perform()` calls either, so there's no mechanical way
to make this test's request differ in shape from `AUTH-001-AC-15`'s "no cookie" row, even deliberately.

**Fix**: the value AC-17 adds isn't in the request being novel — it's in chaining the *real* calls
(an actual `POST /api/auth/logout`, then `GET /api/auth/me` with no cookie attached) rather than
hand-constructing a cookie-less request from scratch, so the test proves logout's actual effect
causes the rejection, instead of asserting two unrelated facts independently and trusting they
compose correctly.

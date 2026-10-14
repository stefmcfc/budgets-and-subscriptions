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
entirely rather than resolving it: `AuthControllerSpec` (`@WebMvcTest`) replaces `JwtBuilder` with a
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

**Context**: building `JwtBuilder` against `jjwt-api:0.12.6`, `.signWith(Jwts.SIG.HS256, secretKey)`
failed to resolve, and `.build()` didn't return a `String`.

**Why**: the legacy JJWT API (pre-0.12) was `signWith(SignatureAlgorithm, Key)` — algorithm first.
0.12.x's `JwtBuilder.signWith` overload takes `(SecretKey, SecureDigestAlgorithm<?,?>)` —
**key first, algorithm second**. Easy to get backwards if a search turns up older JJWT examples.
Separately, `Jwts.builder()...` returns JJWT's own builder type throughout the chain; the method
that actually serializes it into the final compact JWS string is `.compact()`, not `.build()` (which
doesn't exist on this interface for that purpose) — assigning the result of a chain ending in
anything but `.compact()` to a `String` won't type-check.

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

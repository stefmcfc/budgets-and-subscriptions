# Basic Login (Frontend)

**Status**: Not started
**Priority**: P0 — frontend half of build order step 1; every other frontend feature sits behind
a logged-in session existing, and nothing can be route-guarded until this exists.
**Depends on**: `backend_spec_001_basic_login.md` (every endpoint/contract consumed here)
**Area**: Frontend

## Overview

This delivers the frontend half of stage 1 authentication: a registration form and a login form
that talk to `backend_spec_001_basic_login.md`'s endpoints, a React auth context that resolves
whether a session already exists on app load (via `GET /api/auth/me`, since the access-token
cookie is httpOnly and unreadable from JS) and exposes the current user to the rest of the app,
route guarding that keeps protected pages unreachable until that initial check resolves (no flash
of protected content), and a logout action. Registration mirrors the backend's auto-login
behavior (`AUTH-001-AC-05`) — a successful registration response is treated identically to a
successful login response, with no separate login call required. There is deliberately no
refresh/token-renewal behavior in this spec: a session simply expires 15 minutes after
login/registration and the user must log in again — that's stage 2 (`refresh_tokens`)'s job.
Admin-specific UI and email-verification UI are likewise out of scope here, per
`.claude/01-authentication/design-decisions.md`'s spec breakdown and
`.claude/01-authentication/spec_candidates.md`'s deferrals.

## Requirements

### Requirement 1: Registration form

**User story**: As a new visitor, I want to register with my email, password, and display name so
that I'm signed in immediately afterward, without a separate login step.

#### AUTH-002-AC-01 [AUTO]: Accessible, labeled fields
**Statement**: The `RegisterForm` component shall render a `<form>` with labeled email, password,
and display-name inputs, each associated with its `<label>` via matching `htmlFor`/`id`, and a
submit button.

**Rationale**: `frontend_conventions.md`'s accessibility rules — every input needs an associated
label, not just a placeholder.

#### AUTH-002-AC-02 [AUTO]: Correct input types and autocomplete attributes
**Statement**: The `RegisterForm` component shall set `type="email"` and `autocomplete="email"` on
the email input, `type="password"` and `autocomplete="new-password"` on the password input, and
`autocomplete="nickname"` on the display-name input.

**Rationale**: Per the `modern-web-guidance:autofill-sign-up-form` guide — `new-password` cues the
browser's password manager to offer generating/saving a *new* credential, distinct from
autofilling an existing one on a login form; `nickname` is used rather than the more common `name`
token because `design-decisions.md`'s "Display name" section explicitly describes `displayName` as
free text, not a legal first/last name.

#### AUTH-002-AC-03 [AUTO]: Client-side validation gates submission
**Statement**: While the entered password is under 12 characters, the entered email is not
syntactically valid, or the display-name field is blank, the `RegisterForm` component shall keep
the submit action disabled and display validation guidance beside the offending field(s), so a
request is never sent for an input the backend is already known to reject.

**Rationale**: Mirrors the backend's own rules (`AUTH-001-AC-07`, `AUTH-001-AC-08`) client-side,
for immediate feedback instead of a round trip. This also narrows what AUTH-002-AC-07 below needs
to handle — see that AC's rationale.

#### AUTH-002-AC-04 [AUTO]: Pending state while submitting
**Statement**: While a registration request is in flight, the `RegisterForm` component shall
disable the submit button and display a loading indicator with `role="status"`.

**Rationale**: `frontend_conventions.md`'s a11y rule — loading indicators need `role="status"`.

#### AUTH-002-AC-05 [AUTO]: Successful registration stores the session and navigates into the app
**Statement**: When `POST /api/auth/register` responds `201 Created` with
`{ id, email, displayName, role }` (`AUTH-001-AC-05`), the `RegisterForm` component shall store
that user in the auth context and navigate to the app's main route, with no separate login call
made.

**Rationale**: `design-decisions.md`'s "Registration auto-login" section — the backend already
signs the user in on successful registration; the frontend must treat that response exactly like a
successful login, not prompt for credentials again.

#### AUTH-002-AC-06 [AUTO]: Duplicate email shows a field-specific error
**Statement**: If `POST /api/auth/register` rejects with `409 Conflict` (`AUTH-001-AC-06`), then
the `RegisterForm` component shall display an error message associated with the email field
stating that the email is already registered, and shall not navigate away from the form.

**Rationale**: Email-uniqueness is inherently server-known — unlike password length or email
syntax, no client-side check can catch this before submission, so it's the one registration
failure genuinely worth a field-specific message.

#### AUTH-002-AC-07 [AUTO]: Any other registration failure shows a generic, retry-able error
**Statement**: If `POST /api/auth/register` rejects with any status other than `409` (for example
a `400` the client-side check in AUTH-002-AC-03 didn't anticipate, or a network/`5xx` failure),
then the `RegisterForm` component shall display a generic, retry-able error message near the top
of the form and shall not navigate away.

**Rationale**: `backend_spec_001_basic_login.md`'s `AUTH-001-AC-07`/`AUTH-001-AC-08` contract only
the `400` status code, not a field-identifying error body shape — the frontend has no contracted
way to tell *which* field a stray `400` is about. AUTH-002-AC-03's client-side validation already
gates the known causes before a request is sent, so treating any other failure uniformly here is
honest about what's actually been contracted, rather than guessing at an unspecified body shape.

### Requirement 2: Login form

**User story**: As a registered user, I want to log in with my email and password so that I'm
authenticated for the rest of the app.

#### AUTH-002-AC-08 [AUTO]: Accessible, labeled fields with correct autofill attributes
**Statement**: The `LoginForm` component shall render a `<form>` with labeled email and password
inputs — `type="email"` and `autocomplete="email"` on the email input, `type="password"` and
`autocomplete="current-password"` on the password input — each associated with its `<label>`, and
a submit button.

**Rationale**: Per the `modern-web-guidance:autofill-sign-in-form` guide — `current-password`
(not `new-password`) tells the browser to autofill an existing credential rather than offer to
generate one. `autocomplete="email"` rather than the more generic `username` token, because
`design-decisions.md`'s "Registration / login identifier" section makes email the sole login
identifier — there's no separate username to disambiguate.

#### AUTH-002-AC-09 [AUTO]: Pending state while submitting
**Statement**: While a login request is in flight, the `LoginForm` component shall disable the
submit button and display a loading indicator with `role="status"`.

**Rationale**: Same a11y rule as AUTH-002-AC-04.

#### AUTH-002-AC-10 [AUTO]: Successful login stores the session and navigates into the app
**Statement**: When `POST /api/auth/login` responds `200 OK` with
`{ id, email, displayName, role }` (`AUTH-001-AC-10`), the `LoginForm` component shall store that
user in the auth context and navigate to the app's main route.

**Rationale**: Mirrors AUTH-002-AC-05's registration behavior for the login path.

#### AUTH-002-AC-11 [AUTO]: Failed login shows one generic error, not distinguishing cause
**Statement**: If `POST /api/auth/login` rejects with `401 Unauthorized` (`AUTH-001-AC-11`), then
the `LoginForm` component shall display a single generic error message (for example, "Invalid
email or password") without any wording that distinguishes an unrecognized email from a wrong
password, and shall not navigate away from the form.

**Rationale**: Mirrors `AUTH-001-AC-11`'s own uniformity requirement on the frontend side — the
backend deliberately collapses the two causes into one `401` specifically so emails-in-use can't be
enumerated; the frontend must not re-introduce that distinction via copy it invents itself (e.g.
"no account found for this email").

#### AUTH-002-AC-12 [AUTO]: Any other login failure shows a generic, retry-able error
**Statement**: If `POST /api/auth/login` rejects with any status other than `401` (for example a
network or `5xx` failure), then the `LoginForm` component shall display a generic, retry-able
error message and shall not navigate away.

**Rationale**: Same reasoning as AUTH-002-AC-07 — nothing beyond the status code is contracted for
these cases.

### Requirement 3: Session state / auth context

**User story**: As a user whose browser already holds a valid session cookie, I want the app to
recognize me on load (including after a page refresh) without making me log in again.

#### AUTH-002-AC-13 [AUTO]: App load resolves the session exactly once
**Statement**: When the application first mounts, the `AuthProvider` shall call
`GET /api/auth/me` exactly once and populate its user state from a `200 OK` response's
`{ id, email, displayName, role }` body (`AUTH-001-AC-14`).

**Rationale**: `design-decisions.md`'s "Display name" section — `GET /api/auth/me` exists
specifically because the frontend can never read the httpOnly access-token cookie itself, so this
is the only way to answer "is anyone logged in" after a refresh.

#### AUTH-002-AC-14 [AUTO]: An unauthenticated result is not treated as an application error
**Statement**: If `GET /api/auth/me` rejects with `401 Unauthorized` (`AUTH-001-AC-15`), then the
`AuthProvider` shall set its user state to null without surfacing an error message to the user.

**Rationale**: "No one is logged in yet" is the ordinary, expected state for a first-time visitor
or an expired session — distinct from a genuine network/server failure, which would warrant a
visible error elsewhere.

#### AUTH-002-AC-15 [AUTO]: Session-resolution status is observable while in flight
**Statement**: While the initial `GET /api/auth/me` call has not yet resolved, the `AuthProvider`
shall expose an `isLoading` state of `true`, distinct from both "authenticated" (`user` set) and
"unauthenticated" (`user` null, `isLoading` false).

**Rationale**: A three-state model ("don't know yet" / authenticated / unauthenticated) is what
lets AUTH-002-AC-20 avoid ever flashing protected content before the check resolves — a boolean
`user`-is-null check alone can't distinguish "known logged out" from "not checked yet."

#### AUTH-002-AC-16 [AUTO]: Auth context exposes a single, shared shape
**Statement**: The `useAuth` hook shall expose the current user (or `null`), the `isLoading`
state, a `setUser` function to record the user after a successful login/registration response, and
a `logout` function — as the single source of truth consumed by `RegisterForm`, `LoginForm`,
`ProtectedRoute`, and the logout action.

**Rationale**: `frontend_conventions.md`'s "avoid prop drilling; lift state up or use Context"
guidance — one context is the single place session state lives, rather than each consumer tracking
its own copy.

#### AUTH-002-AC-17 [AUTO]: No credential or session data persisted to browser storage
**Statement**: The `AuthProvider` shall hold the current user only in memory (React state), and
shall never write the password, any token value, or the user object itself to `localStorage` or
`sessionStorage`, under any outcome.

**Rationale**: The session lives entirely in the httpOnly cookie by design
(`design-decisions.md`'s "Transport & CSRF" section) — this is also *why* AUTH-002-AC-13 has to
re-fetch `/me` on every app load: there is deliberately nothing persisted client-side to restore
from.

### Requirement 4: Route guarding

**User story**: As a user without a valid session, I want to be redirected to the login page when
I try to reach a protected part of the app, so unauthenticated access isn't possible.

#### AUTH-002-AC-18 [AUTO]: Protected routes redirect when unauthenticated
**Statement**: While the auth context's initial session check has resolved (`isLoading` false)
and reports no user, the `ProtectedRoute` component shall redirect to the login route instead of
rendering its protected children.

**Rationale**: The core guarding behavior this requirement exists for.

#### AUTH-002-AC-19 [AUTO]: Protected routes render once authenticated
**Statement**: While the auth context reports an authenticated user, the `ProtectedRoute`
component shall render its protected children.

**Rationale**: The complementary positive case to AUTH-002-AC-18.

#### AUTH-002-AC-20 [AUTO]: No flash of protected content while the session check is pending
**Statement**: While the auth context's initial session check has not yet resolved (`isLoading`
true), the `ProtectedRoute` component shall render neither its protected children nor a redirect,
instead rendering a loading state.

**Rationale**: Without this, a page refresh on a protected route would render the redirect (or
worse, the protected content) before `GET /api/auth/me` has had a chance to resolve, since
`isLoading` only exists because of AUTH-002-AC-15 — this AC is the reason that third state exists.

### Requirement 5: Logout

**User story**: As a logged-in user, I want to log out so that my session ends and I'm returned to
the login page.

#### AUTH-002-AC-21 [AUTO]: Successful logout clears session state and navigates to login
**Statement**: When `POST /api/auth/logout` responds `200 OK` (`AUTH-001-AC-16`), the `logout`
action shall clear the auth context's user state and navigate to the login route.

**Rationale**: Matches the backend contract — logout in this stage has no server-side revocation
beyond the cookie clear (`AUTH-001-AC-16`'s rationale), so the frontend's job is simply to reflect
that the cookie is now gone.

#### AUTH-002-AC-22 [AUTO]: A failed logout call leaves session state unchanged
**Statement**: If `POST /api/auth/logout` fails (a non-`200` response or a network error), then
the `logout` action shall leave the auth context's user state unchanged, display a generic,
retry-able error message, and shall not navigate away from the current page.

**Rationale**: If the server-side cookie clear didn't actually happen, the access-token cookie is
still live — clearing local state and navigating to login anyway would show the user a "logged
out" screen while their session (and access to protected routes, if they navigate back) is still
genuinely active. Leaving state as-is keeps the UI honest about what's actually true.

## Cross-references

| Contract | Defined here | Used by |
|---|---|---|
| `POST /api/auth/register` (`201` body; `409`; `400`) | `backend_spec_001_basic_login.md` (`AUTH-001-AC-05`..`09`) | Requirement 1 |
| `POST /api/auth/login` (`200` body; `401`) | `backend_spec_001_basic_login.md` (`AUTH-001-AC-10`..`13`) | Requirement 2 |
| `GET /api/auth/me` (`200` body; `401`) | `backend_spec_001_basic_login.md` (`AUTH-001-AC-14`, `15`) | Requirement 3 |
| `POST /api/auth/logout` (`200`) | `backend_spec_001_basic_login.md` (`AUTH-001-AC-16`, `17`) | Requirement 5 |
| `AuthUser` type `{ id, email, displayName, role }` | This spec — new `frontend/src/types/auth.ts` | `authApi`, `AuthProvider`/`useAuth`, both forms, `ProtectedRoute` |
| `authApi` service (`register`, `login`, `me`, `logout`) | This spec — new `frontend/src/services/authApi.ts` | `RegisterForm`, `LoginForm`, `AuthProvider` |
| `AuthProvider`/`useAuth` context | This spec — new `frontend/src/context/AuthContext.tsx` | `RegisterForm`, `LoginForm`, `ProtectedRoute`, logout action |
| `ApiError` type (`status`, `message`, `details`) | `frontend_conventions.md` | AUTH-002-AC-07, `12`, `22` (generic-failure handling) |
| Password policy (min 12 chars, no complexity rule) | `.claude/01-authentication/design-decisions.md` ("Password hashing", "Registration / login identifier") | AUTH-002-AC-03 |
| `displayName` is free text, not a legal name | `.claude/01-authentication/design-decisions.md` ("Display name") | AUTH-002-AC-02 (`autocomplete="nickname"`) |
| Registration auto-login | `.claude/01-authentication/design-decisions.md` ("Registration auto-login") | AUTH-002-AC-05 |
| httpOnly cookie / no client-readable token | `.claude/01-authentication/design-decisions.md` ("Transport & CSRF") | AUTH-002-AC-13, `17` |
| Sign-up/sign-in autofill guidance | `modern-web-guidance` skill (`autofill-sign-up-form`, `autofill-sign-in-form`) | AUTH-002-AC-02, `08` |
| No refresh/token-renewal behavior (explicitly out of scope here) | `.claude/01-authentication/design-decisions.md` ("Spec breakdown", stage 2) | N/A — confirms this spec's session simply expires after 15 minutes |
| Admin UI, email-verification UI (explicitly deferred) | `spec_candidates.md`; `design-decisions.md` ("Spec breakdown", stage 3) | N/A — confirms neither belongs in this spec despite `role` already being present in session state |

## Acceptance Criteria Summary

- [ ] AUTH-002-AC-01 [AUTO]: Registration form renders accessible, labeled fields + submit button
- [ ] AUTH-002-AC-02 [AUTO]: Registration fields use correct `type`/`autocomplete` attributes
- [ ] AUTH-002-AC-03 [AUTO]: Client-side validation gates registration submission
- [ ] AUTH-002-AC-04 [AUTO]: Registration shows a pending state while submitting
- [ ] AUTH-002-AC-05 [AUTO]: Successful registration stores session and navigates, no separate login
- [ ] AUTH-002-AC-06 [AUTO]: Duplicate email (409) shows a field-specific error, no navigation
- [ ] AUTH-002-AC-07 [AUTO]: Any other registration failure shows a generic, retry-able error
- [ ] AUTH-002-AC-08 [AUTO]: Login form renders accessible fields with correct autofill attributes
- [ ] AUTH-002-AC-09 [AUTO]: Login shows a pending state while submitting
- [ ] AUTH-002-AC-10 [AUTO]: Successful login stores session and navigates into the app
- [ ] AUTH-002-AC-11 [AUTO]: Failed login (401) shows one generic error, no cause distinction
- [ ] AUTH-002-AC-12 [AUTO]: Any other login failure shows a generic, retry-able error
- [ ] AUTH-002-AC-13 [AUTO]: App load resolves the session via `GET /api/auth/me` exactly once
- [ ] AUTH-002-AC-14 [AUTO]: An unauthenticated (401) result isn't treated as an app error
- [ ] AUTH-002-AC-15 [AUTO]: `isLoading` is a distinct third state while the session check is pending
- [ ] AUTH-002-AC-16 [AUTO]: `useAuth` exposes `user`, `isLoading`, `setUser`, `logout`
- [ ] AUTH-002-AC-17 [AUTO]: No credential/session data persisted to browser storage
- [ ] AUTH-002-AC-18 [AUTO]: `ProtectedRoute` redirects to login when unauthenticated
- [ ] AUTH-002-AC-19 [AUTO]: `ProtectedRoute` renders protected content when authenticated
- [ ] AUTH-002-AC-20 [AUTO]: No flash of protected content while the session check is pending
- [ ] AUTH-002-AC-21 [AUTO]: Successful logout clears session state and navigates to login
- [ ] AUTH-002-AC-22 [AUTO]: Failed logout leaves session state unchanged, shows a generic error

## Test case sketches

Per `ears_format.md`'s frontend convention, tests are grouped by component/module file, with one
`describe` block per requirement ID. These are fuller than the backend's block-structure-only
sketches (`frontend-dev` implements these directly), but still illustrative — exact selectors,
copy, and mock shapes may be adjusted during implementation as long as the AC's behavior holds.

### `frontend/src/components/Auth/RegisterForm.test.tsx` — AUTH-002-AC-01..07

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { RegisterForm } from './RegisterForm';
import { authApi } from '../../services/authApi';
import { useAuth } from '../../context/AuthContext';
import { ApiError } from '../../types/api';

vi.mock('../../services/authApi');
vi.mock('../../context/AuthContext');

const mockRegister = vi.mocked(authApi.register);
const mockSetUser = vi.fn();
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

function renderForm() {
  render(
    <MemoryRouter>
      <RegisterForm />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(email: string, password: string, displayName: string) {
  await userEvent.type(screen.getByLabelText(/email/i), email);
  await userEvent.type(screen.getByLabelText(/password/i), password);
  await userEvent.type(screen.getByLabelText(/display name/i), displayName);
  await userEvent.click(screen.getByRole('button', { name: /register|sign up/i }));
}

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({
    user: null,
    isLoading: false,
    setUser: mockSetUser,
    logout: vi.fn(),
  });
});

describe('AUTH-002-AC-01: accessible, labeled fields', () => {
  it('renders labeled email, password, and display name inputs plus a submit button', () => {
    renderForm();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /register|sign up/i })).toBeInTheDocument();
  });
});

describe('AUTH-002-AC-02: correct input types and autocomplete attributes', () => {
  it('sets type/autocomplete per sign-up form guidance', () => {
    renderForm();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('type', 'email');
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText(/display name/i)).toHaveAttribute('autocomplete', 'nickname');
  });
});

describe('AUTH-002-AC-03: client-side validation gates submission', () => {
  it('shows an inline error and never calls the API for a too-short password', async () => {
    renderForm();
    await fillAndSubmit('new@example.com', 'short1', 'Steve');

    expect(await screen.findByText(/at least 12 characters/i)).toBeInTheDocument();
    expect(mockRegister).not.toHaveBeenCalled();
  });
});

describe('AUTH-002-AC-04: pending state while submitting', () => {
  it('disables the submit button and shows a status indicator mid-request', async () => {
    mockRegister.mockImplementation(() => new Promise(() => {}));
    renderForm();
    await fillAndSubmit('new@example.com', 'a-long-enough-password', 'Steve');

    expect(screen.getByRole('button', { name: /register|sign up/i })).toBeDisabled();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});

describe('AUTH-002-AC-05: successful registration stores session and navigates', () => {
  it('calls setUser with the returned user and navigates into the app, no login call made', async () => {
    const user = { id: '1', email: 'new@example.com', displayName: 'Steve', role: 'USER' as const };
    mockRegister.mockResolvedValue(user);
    renderForm();
    await fillAndSubmit(user.email, 'a-long-enough-password', user.displayName);

    await waitFor(() => expect(mockSetUser).toHaveBeenCalledWith(user));
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });
});

describe('AUTH-002-AC-06: duplicate email shows a field-specific error', () => {
  it('shows an email-field error and does not navigate on 409', async () => {
    mockRegister.mockRejectedValue(new ApiError(409, 'Email already registered'));
    renderForm();
    await fillAndSubmit('taken@example.com', 'a-long-enough-password', 'Steve');

    expect(await screen.findByText(/already registered/i)).toBeInTheDocument();
    expect(mockSetUser).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});

describe('AUTH-002-AC-07: any other failure shows a generic, retry-able error', () => {
  it('shows a generic error on an unexpected 400/5xx/network failure', async () => {
    mockRegister.mockRejectedValue(new ApiError(500, 'Internal Server Error'));
    renderForm();
    await fillAndSubmit('new@example.com', 'a-long-enough-password', 'Steve');

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i);
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
```

### `frontend/src/components/Auth/LoginForm.test.tsx` — AUTH-002-AC-08..12

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LoginForm } from './LoginForm';
import { authApi } from '../../services/authApi';
import { useAuth } from '../../context/AuthContext';
import { ApiError } from '../../types/api';

vi.mock('../../services/authApi');
vi.mock('../../context/AuthContext');

const mockLogin = vi.mocked(authApi.login);
const mockSetUser = vi.fn();
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

function renderForm() {
  render(
    <MemoryRouter>
      <LoginForm />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(email: string, password: string) {
  await userEvent.type(screen.getByLabelText(/email/i), email);
  await userEvent.type(screen.getByLabelText(/password/i), password);
  await userEvent.click(screen.getByRole('button', { name: /log in|sign in/i }));
}

beforeEach(() => {
  vi.mocked(useAuth).mockReturnValue({
    user: null,
    isLoading: false,
    setUser: mockSetUser,
    logout: vi.fn(),
  });
});

describe('AUTH-002-AC-08: accessible, labeled fields with correct autofill attributes', () => {
  it('renders labeled email/password inputs with the sign-in autocomplete tokens', () => {
    renderForm();
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('button', { name: /log in|sign in/i })).toBeInTheDocument();
  });
});

describe('AUTH-002-AC-09: pending state while submitting', () => {
  it('disables the submit button and shows a status indicator mid-request', async () => {
    mockLogin.mockImplementation(() => new Promise(() => {}));
    renderForm();
    await fillAndSubmit('user@example.com', 'a-long-enough-password');

    expect(screen.getByRole('button', { name: /log in|sign in/i })).toBeDisabled();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});

describe('AUTH-002-AC-10: successful login stores session and navigates', () => {
  it('calls setUser with the returned user and navigates into the app', async () => {
    const user = { id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' as const };
    mockLogin.mockResolvedValue(user);
    renderForm();
    await fillAndSubmit(user.email, 'a-long-enough-password');

    await waitFor(() => expect(mockSetUser).toHaveBeenCalledWith(user));
    expect(mockNavigate).toHaveBeenCalledWith('/');
  });
});

describe('AUTH-002-AC-11: failed login shows one generic error, no cause distinction', () => {
  it('shows a single generic message on 401, without naming which field was wrong', async () => {
    mockLogin.mockRejectedValue(new ApiError(401, 'Unauthorized'));
    renderForm();
    await fillAndSubmit('user@example.com', 'wrong-password');

    const error = await screen.findByRole('alert');
    expect(error).toHaveTextContent(/invalid email or password/i);
    expect(error).not.toHaveTextContent(/no account|not registered|unknown email/i);
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});

describe('AUTH-002-AC-12: any other login failure shows a generic, retry-able error', () => {
  it('shows a generic error on an unexpected 5xx/network failure', async () => {
    mockLogin.mockRejectedValue(new ApiError(500, 'Internal Server Error'));
    renderForm();
    await fillAndSubmit('user@example.com', 'a-long-enough-password');

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i);
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
```

### `frontend/src/context/AuthContext.test.tsx` — AUTH-002-AC-13..17, 21, 22

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from './AuthContext';
import { authApi } from '../services/authApi';
import { ApiError } from '../types/api';

vi.mock('../services/authApi');

const mockMe = vi.mocked(authApi.me);
const mockLogout = vi.mocked(authApi.logout);
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

function Consumer() {
  const { user, isLoading, logout } = useAuth();
  if (isLoading) return <div role="status">loading</div>;
  return (
    <div>
      <span data-testid="user">{user ? user.displayName : 'anonymous'}</span>
      <button onClick={() => logout()}>Log out</button>
    </div>
  );
}

function renderProvider() {
  render(
    <MemoryRouter>
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AUTH-002-AC-13: resolves the session via GET /api/auth/me exactly once on mount', () => {
  it('calls authApi.me once and populates the user from a 200 response', async () => {
    const user = { id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' as const };
    mockMe.mockResolvedValue(user);
    renderProvider();

    expect(await screen.findByTestId('user')).toHaveTextContent('Steve');
    expect(mockMe).toHaveBeenCalledTimes(1);
  });
});

describe('AUTH-002-AC-14: an unauthenticated result is not treated as an app error', () => {
  it('sets user to null on 401 without rendering an error', async () => {
    mockMe.mockRejectedValue(new ApiError(401, 'Unauthorized'));
    renderProvider();

    expect(await screen.findByTestId('user')).toHaveTextContent('anonymous');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('AUTH-002-AC-15: isLoading is a distinct third state while /me is pending', () => {
  it('renders the loading state before the /me call resolves', async () => {
    mockMe.mockImplementation(() => new Promise(() => {}));
    renderProvider();

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByTestId('user')).not.toBeInTheDocument();
  });
});

describe('AUTH-002-AC-16: useAuth exposes the shared context shape', () => {
  it('provides user, isLoading, setUser, and logout to consumers', async () => {
    mockMe.mockResolvedValue(null as never);
    let captured: ReturnType<typeof useAuth> | undefined;
    function Capture() {
      captured = useAuth();
      return null;
    }
    render(
      <MemoryRouter>
        <AuthProvider>
          <Capture />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(captured).toBeDefined());
    expect(captured).toEqual(
      expect.objectContaining({
        user: expect.anything(),
        isLoading: expect.any(Boolean),
        setUser: expect.any(Function),
        logout: expect.any(Function),
      }),
    );
  });
});

describe('AUTH-002-AC-17: no credential/session data persisted to browser storage', () => {
  it('leaves localStorage and sessionStorage empty after resolving the session', async () => {
    const user = { id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' as const };
    mockMe.mockResolvedValue(user);
    renderProvider();

    await screen.findByTestId('user');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});

describe('AUTH-002-AC-21: successful logout clears session state and navigates to login', () => {
  it('clears the user and navigates to /login on a 200 response', async () => {
    mockMe.mockResolvedValue({ id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' });
    mockLogout.mockResolvedValue(undefined);
    renderProvider();
    await screen.findByTestId('user');

    await userEvent.click(screen.getByRole('button', { name: /log out/i }));

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('anonymous'));
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });
});

describe('AUTH-002-AC-22: a failed logout call leaves session state unchanged', () => {
  it('keeps the current user and shows a generic error when the logout call fails', async () => {
    const user = { id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' as const };
    mockMe.mockResolvedValue(user);
    mockLogout.mockRejectedValue(new ApiError(500, 'Internal Server Error'));
    renderProvider();
    await screen.findByTestId('user');

    await userEvent.click(screen.getByRole('button', { name: /log out/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i);
    expect(screen.getByTestId('user')).toHaveTextContent('Steve');
    expect(mockNavigate).not.toHaveBeenCalledWith('/login');
  });
});
```

### `frontend/src/components/Auth/ProtectedRoute.test.tsx` — AUTH-002-AC-18..20

```tsx
import { render, screen } from '@testing-library/react';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuth } from '../../context/AuthContext';

vi.mock('../../context/AuthContext');

function renderGuarded() {
  render(
    <MemoryRouter initialEntries={['/protected']}>
      <ProtectedRoute>
        <div>Protected content</div>
      </ProtectedRoute>
    </MemoryRouter>,
  );
}

describe('AUTH-002-AC-18: redirects to login when unauthenticated', () => {
  it('does not render protected children once the session check resolves with no user', () => {
    vi.mocked(useAuth).mockReturnValue({ user: null, isLoading: false, setUser: vi.fn(), logout: vi.fn() });
    renderGuarded();

    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });
});

describe('AUTH-002-AC-19: renders protected content when authenticated', () => {
  it('renders children once the session check resolves with a user', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: { id: '1', email: 'user@example.com', displayName: 'Steve', role: 'USER' },
      isLoading: false,
      setUser: vi.fn(),
      logout: vi.fn(),
    });
    renderGuarded();

    expect(screen.getByText('Protected content')).toBeInTheDocument();
  });
});

describe('AUTH-002-AC-20: no flash of protected content while the session check is pending', () => {
  it('renders a loading state, not the children or a redirect, while isLoading is true', () => {
    vi.mocked(useAuth).mockReturnValue({ user: null, isLoading: true, setUser: vi.fn(), logout: vi.fn() });
    renderGuarded();

    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
```

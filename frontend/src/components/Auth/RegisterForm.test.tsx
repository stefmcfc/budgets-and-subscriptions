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
  vi.clearAllMocks();
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

  it('disables the submit button while any field is invalid', () => {
    renderForm();
    expect(screen.getByRole('button', { name: /register|sign up/i })).toBeDisabled();
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

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
  vi.clearAllMocks();
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

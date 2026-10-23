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
    mockMe.mockRejectedValue(new ApiError(401, 'Unauthorized'));
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

    await waitFor(() => expect(captured?.isLoading).toBe(false));
    expect(captured).toEqual(
      expect.objectContaining({
        user: null,
        isLoading: false,
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

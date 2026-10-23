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

beforeEach(() => {
  vi.clearAllMocks();
});

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

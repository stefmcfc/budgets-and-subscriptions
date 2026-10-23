import { useAuth } from '../context/AuthContext';

/**
 * Placeholder landing page behind ProtectedRoute, proving the auth/routing stack works
 * end-to-end. Real app content arrives with the accounts/transactions build-order step.
 */
export function DashboardPage() {
  const { user, logout } = useAuth();

  return (
    <div>
      <h1>Welcome{user ? `, ${user.displayName}` : ''}</h1>
      <button type="button" onClick={() => logout()}>
        Log out
      </button>
    </div>
  );
}

import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
}

/**
 * Gates its children behind the auth context's session state. The three-state model
 * (loading / authenticated / unauthenticated) is what lets this avoid ever flashing protected
 * content before the initial GET /api/auth/me resolves (AUTH-002-AC-20) — a boolean
 * `user`-is-null check alone can't distinguish "known logged out" from "not checked yet".
 */
export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <div role="status">Checking your session…</div>;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

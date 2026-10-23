import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/authApi';
import type { AuthUser } from '../types/auth';

export interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  setUser: (user: AuthUser | null) => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const navigate = useNavigate();

  // AUTH-002-AC-13: resolve the session exactly once on mount. The httpOnly access-token cookie
  // is unreadable from JS, so GET /api/auth/me is the only way to answer "is anyone logged in"
  // after a page refresh — there is nothing persisted client-side to restore from instead
  // (AUTH-002-AC-17).
  useEffect(() => {
    let isMounted = true;

    authApi
      .me()
      .then((currentUser) => {
        if (isMounted) {
          setUser(currentUser);
        }
      })
      .catch(() => {
        // AUTH-002-AC-14: an unauthenticated 401 here is the ordinary "no one is logged in yet"
        // state, not an application error — no error message is surfaced for it.
        if (isMounted) {
          setUser(null);
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const logout = async () => {
    try {
      await authApi.logout();
      setUser(null);
      setLogoutError(null);
      navigate('/login');
    } catch {
      // AUTH-002-AC-22: if the server-side cookie clear didn't happen, the session is still
      // genuinely active — leave state as-is rather than showing a "logged out" screen that
      // isn't true.
      setLogoutError('Something went wrong. Please try again.');
    }
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, setUser, logout }}>
      {logoutError && <div role="alert">{logoutError}</div>}
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

import axios from 'axios';
import { ApiError } from '../types/api';

// backend_spec_001_basic_login.md's auth routes are unversioned (`/api/auth/...`, not
// `/api/v1/auth/...`) — confirmed against the live backend. `frontend_conventions.md`'s `/api/v1`
// example predates that decision; this fallback matches what's actually implemented today.
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8090/api';

/**
 * `withCredentials` is required even behind the dev-server proxy — the access-token cookie is
 * httpOnly and set by the backend, but axios still needs to be told to send/accept cookies on
 * cross-origin (production) requests rather than defaulting to omitting them.
 */
export const client = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
});

interface BackendErrorBody {
  message?: string;
  details?: unknown;
}

/**
 * Wraps a raw axios call, normalizing any failure into a typed `ApiError` so components never
 * see an axios-specific error shape. Call sites in `*Api.ts` files should never call `client`
 * directly without going through this.
 */
export async function request<T>(fn: () => Promise<{ data: T }>): Promise<T> {
  try {
    const response = await fn();
    return response.data;
  } catch (error) {
    if (axios.isAxiosError<BackendErrorBody>(error)) {
      const status = error.response?.status ?? 0;
      const message = error.response?.data?.message ?? error.message;
      const details = error.response?.data?.details;
      throw new ApiError(status, message, details);
    }
    throw new ApiError(0, 'Network error. Please check your connection and try again.');
  }
}

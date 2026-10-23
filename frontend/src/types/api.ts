/**
 * Thrown by the `request<T>()` wrapper (`src/services/client.ts`) for any failed backend call —
 * a non-2xx HTTP response or a network failure. Components branch on `status`; `message` is safe
 * to show to the user as-is, `details` is not (see frontend_conventions.md's Error Handling rules).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

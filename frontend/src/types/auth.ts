export type Role = 'USER' | 'ADMIN';

/**
 * The shape returned by every auth endpoint that identifies the current user
 * (`register`/`login`/`me`). Never includes a password or token — the access token lives only in
 * the httpOnly cookie and is never readable from JS.
 */
export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  role: Role;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

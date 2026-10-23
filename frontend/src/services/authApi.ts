import { client, request } from './client';
import type { AuthUser, LoginInput, RegisterInput } from '../types/auth';

export const authApi = {
  register: (input: RegisterInput): Promise<AuthUser> =>
    request<AuthUser>(() => client.post('/auth/register', input)),

  login: (input: LoginInput): Promise<AuthUser> =>
    request<AuthUser>(() => client.post('/auth/login', input)),

  me: (): Promise<AuthUser> => request<AuthUser>(() => client.get('/auth/me')),

  logout: (): Promise<void> => request<void>(() => client.post('/auth/logout')),
};

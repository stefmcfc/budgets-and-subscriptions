import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../services/authApi';
import { useAuth } from '../../context/AuthContext';
import { ApiError } from '../../types/api';
import styles from './AuthForm.module.css';

export function LoginForm() {
  const { setUser } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const handleEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    setEmail(event.target.value);
    setFormError(null);
  };

  const handlePasswordChange = (event: ChangeEvent<HTMLInputElement>) => {
    setPassword(event.target.value);
    setFormError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    setFormError(null);

    try {
      const user = await authApi.login({ email, password });
      setUser(user);
      navigate('/');
    } catch (error) {
      // AUTH-002-AC-11: a 401 gets one generic message, deliberately never distinguishing an
      // unrecognized email from a wrong password — the backend already collapses both causes
      // into one status specifically so emails-in-use can't be enumerated.
      if (error instanceof ApiError && error.status === 401) {
        setFormError('Invalid email or password.');
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1>Log in</h1>

      {formError && (
        <div role="alert" className={styles.formError}>
          {formError}
        </div>
      )}

      <div className={styles.field}>
        <label htmlFor="login-email">Email</label>
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={handleEmailChange}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="login-password">Password</label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={handlePasswordChange}
        />
      </div>

      <button type="submit" className={styles.submit} disabled={isSubmitting}>
        Log in
      </button>
      {isSubmitting && (
        <p role="status" className={styles.status}>
          Logging in…
        </p>
      )}
    </form>
  );
}

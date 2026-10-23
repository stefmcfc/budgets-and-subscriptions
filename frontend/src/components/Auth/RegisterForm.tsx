import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../services/authApi';
import { useAuth } from '../../context/AuthContext';
import { ApiError } from '../../types/api';
import styles from './AuthForm.module.css';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 12;

interface FormValues {
  email: string;
  password: string;
  displayName: string;
}

interface FieldErrors {
  email?: string;
  password?: string;
  displayName?: string;
}

function validate(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (!EMAIL_PATTERN.test(values.email)) {
    errors.email = 'Enter a valid email address.';
  }
  if (values.password.length < MIN_PASSWORD_LENGTH) {
    errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
  }
  if (values.displayName.trim().length === 0) {
    errors.displayName = 'Display name is required.';
  }
  return errors;
}

/**
 * Registration mirrors the backend's auto-login behavior (AUTH-001-AC-05): a successful
 * registration response is treated exactly like a successful login, with no separate login call.
 */
export function RegisterForm() {
  const { setUser } = useAuth();
  const navigate = useNavigate();

  const [values, setValues] = useState<FormValues>({
    email: '',
    password: '',
    displayName: '',
  });
  const [touched, setTouched] = useState<Record<keyof FormValues, boolean>>({
    email: false,
    password: false,
    displayName: false,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [emailTakenError, setEmailTakenError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const fieldErrors = validate(values);
  const hasFieldErrors = Object.keys(fieldErrors).length > 0;

  const handleChange =
    (field: keyof FormValues) => (event: ChangeEvent<HTMLInputElement>) => {
      setValues((previous) => ({ ...previous, [field]: event.target.value }));
      if (field === 'email') {
        setEmailTakenError(null);
      }
      setFormError(null);
    };

  const handleBlur = (field: keyof FormValues) => () => {
    setTouched((previous) => ({ ...previous, [field]: true }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTouched({ email: true, password: true, displayName: true });

    if (hasFieldErrors) {
      return;
    }

    setIsSubmitting(true);
    setFormError(null);
    setEmailTakenError(null);

    try {
      const user = await authApi.register(values);
      setUser(user);
      navigate('/');
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setEmailTakenError('This email is already registered.');
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const emailError = (touched.email && fieldErrors.email) || emailTakenError || undefined;

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1>Register</h1>

      {formError && (
        <div role="alert" className={styles.formError}>
          {formError}
        </div>
      )}

      <div className={styles.field}>
        <label htmlFor="register-email">Email</label>
        <input
          id="register-email"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={handleChange('email')}
          onBlur={handleBlur('email')}
          aria-describedby={emailError ? 'register-email-error' : undefined}
        />
        {emailError && (
          <p id="register-email-error" role="alert" className={styles.error}>
            {emailError}
          </p>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="register-password">Password</label>
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={handleChange('password')}
          onBlur={handleBlur('password')}
          aria-describedby={
            touched.password && fieldErrors.password ? 'register-password-error' : undefined
          }
        />
        {touched.password && fieldErrors.password && (
          <p id="register-password-error" role="alert" className={styles.error}>
            {fieldErrors.password}
          </p>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="register-display-name">Display name</label>
        <input
          id="register-display-name"
          type="text"
          autoComplete="nickname"
          value={values.displayName}
          onChange={handleChange('displayName')}
          onBlur={handleBlur('displayName')}
          aria-describedby={
            touched.displayName && fieldErrors.displayName
              ? 'register-display-name-error'
              : undefined
          }
        />
        {touched.displayName && fieldErrors.displayName && (
          <p id="register-display-name-error" role="alert" className={styles.error}>
            {fieldErrors.displayName}
          </p>
        )}
      </div>

      <button type="submit" className={styles.submit} disabled={isSubmitting || hasFieldErrors}>
        Register
      </button>
      {isSubmitting && (
        <p role="status" className={styles.status}>
          Registering…
        </p>
      )}
    </form>
  );
}

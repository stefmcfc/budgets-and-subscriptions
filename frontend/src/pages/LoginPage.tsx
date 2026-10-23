import { Link } from 'react-router-dom';
import { LoginForm } from '../components/Auth/LoginForm';

export function LoginPage() {
  return (
    <div>
      <LoginForm />
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </div>
  );
}

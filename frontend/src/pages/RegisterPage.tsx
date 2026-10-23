import { Link } from 'react-router-dom';
import { RegisterForm } from '../components/Auth/RegisterForm';

export function RegisterPage() {
  return (
    <div>
      <RegisterForm />
      <p>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </div>
  );
}

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    role text NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX users_email_lower_unique
    ON users (lower(email));
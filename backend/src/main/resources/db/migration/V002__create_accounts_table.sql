CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    opened_on date NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    type text NOT NULL CHECK (type IN ('CURRENT', 'SAVINGS', 'CASH_ISA')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

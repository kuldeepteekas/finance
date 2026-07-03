-- 10-digit account numbers (e.g. 1000000001) — ~9 billion capacity, looks like a real bank account
CREATE SEQUENCE IF NOT EXISTS account_number_seq START WITH 1000000001 INCREMENT BY 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS accounts (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(10)   NOT NULL UNIQUE DEFAULT nextval('account_number_seq')::text,
    user_id        UUID          NOT NULL REFERENCES users(id),
    currency       VARCHAR(10)   NOT NULL REFERENCES currencies(code),
    balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),

    -- Business rules that won't change regardless of currency support
    CONSTRAINT chk_accounts_balance_non_negative
        CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'BLOCKED', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id      ON accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);

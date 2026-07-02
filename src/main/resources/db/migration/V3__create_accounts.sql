CREATE TABLE accounts (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users(id),
    currency    VARCHAR(10)   NOT NULL REFERENCES currencies(code),
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),

    -- Business rules that won't change regardless of currency support
    CONSTRAINT chk_accounts_balance_non_negative
        CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'BLOCKED', 'INACTIVE'))
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

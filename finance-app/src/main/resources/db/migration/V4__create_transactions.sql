CREATE TABLE IF NOT EXISTS transactions (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id           UUID          NOT NULL REFERENCES accounts(id),
    user_id              UUID          NOT NULL REFERENCES users(id),
    type                 VARCHAR(20)   NOT NULL,
    amount               NUMERIC(19,4) NOT NULL,
    currency             VARCHAR(10)   NOT NULL REFERENCES currencies(code),
    balance_before       NUMERIC(19,4) NOT NULL,
    balance_after        NUMERIC(19,4) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    description          VARCHAR(500),
    failure_reason       VARCHAR(500),
    correlation_id       UUID          NOT NULL,
    idempotency_key      VARCHAR(255),
    external_call_status VARCHAR(20),
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW(),

    -- Business rules: fixed transaction lifecycle values
    CONSTRAINT chk_transactions_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_OUT', 'TRANSFER_IN', 'EXCHANGE_OUT', 'EXCHANGE_IN')),
    CONSTRAINT chk_transactions_status
        CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT chk_transactions_external_call_status
        CHECK (external_call_status IS NULL
            OR external_call_status IN ('SUCCESS', 'FAILED', 'TIMED_OUT', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id     ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_correlation_id ON transactions(correlation_id);
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency    ON transactions(idempotency_key);
-- DESC index supports cursor-based pagination (newest first)
CREATE INDEX IF NOT EXISTS idx_transactions_created_at     ON transactions(created_at DESC);

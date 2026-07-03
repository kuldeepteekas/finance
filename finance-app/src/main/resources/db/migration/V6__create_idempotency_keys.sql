CREATE TABLE IF NOT EXISTS idempotency_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    user_id         UUID         NOT NULL REFERENCES users(id),
    response_status INT          NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_key        ON idempotency_keys(idempotency_key);
-- Used by the cleanup job to purge expired keys
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

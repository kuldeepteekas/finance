-- Tracks the other side of an internal transfer:
--   EXCHANGE_OUT: the account that RECEIVED the converted funds
--   EXCHANGE_IN:  the account that SENT the original funds
-- NULL for DEPOSIT and WITHDRAWAL (no internal counterparty).
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS counterparty_account_id UUID REFERENCES accounts(id) NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_counterparty ON transactions(counterparty_account_id)
    WHERE counterparty_account_id IS NOT NULL;

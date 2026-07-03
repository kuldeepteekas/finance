-- Allow TIMED_OUT as a valid external_call_status value.
-- This distinguishes a hung/slow external service (timeout) from a clean error response (FAILED).
ALTER TABLE transactions
    DROP CONSTRAINT chk_transactions_external_call_status;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_external_call_status
        CHECK (external_call_status IS NULL
            OR external_call_status IN ('SUCCESS', 'FAILED', 'TIMED_OUT', 'SKIPPED'));

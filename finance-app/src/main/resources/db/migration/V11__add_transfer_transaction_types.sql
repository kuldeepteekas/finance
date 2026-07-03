-- Add TRANSFER_OUT and TRANSFER_IN transaction types to support same-currency transfers.
-- Previously, transfers between accounts of the same currency were rejected.
-- Now they are recorded as TRANSFER_OUT (debit) / TRANSFER_IN (credit) at a 1:1 rate.
ALTER TABLE transactions
    DROP CONSTRAINT chk_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_type
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_OUT', 'TRANSFER_IN', 'EXCHANGE_OUT', 'EXCHANGE_IN'));

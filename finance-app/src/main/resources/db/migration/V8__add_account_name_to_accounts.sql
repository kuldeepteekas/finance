-- Optional display name to help users distinguish between accounts
-- (e.g. two EUR accounts: "Savings EUR" vs "Daily EUR")
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_name VARCHAR(100);

-- Reference table for supported currencies.
-- Adding a new currency = INSERT a row here + update Java enum.
-- No schema change (no ALTER TABLE, no CHECK constraint edits) needed anywhere else.
CREATE TABLE IF NOT EXISTS currencies (
    code        VARCHAR(10)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================================
-- Seed: Currencies
-- To add support for a new currency in future:
--   1. INSERT a row here (or via a new migration)
--   2. Add the value to the Java Currency enum
--   No schema changes required anywhere else.
-- =============================================================
INSERT INTO currencies (code, name, is_active) VALUES
    ('EUR', 'Euro',              TRUE),
    ('USD', 'US Dollar',         TRUE),
    ('SEK', 'Swedish Krona',     TRUE),
    ('GBP', 'British Pound',     TRUE),
    ('VND', 'Vietnamese Dong',   TRUE);

-- =============================================================
-- Seed: Users
-- Passwords are BCrypt-hashed (cost 10)
--   alice → plaintext: "password"
--   bob   → plaintext: "password2"
-- =============================================================
INSERT INTO users (id, username, email, password, full_name, created_at, updated_at) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'alice', 'alice@example.com', '$2y$10$ntlR.jz4jSkNgaootLpxauNHurCsZYT6NZmJieYgY8jVqDLNTcmGO', 'Alice Smith', NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000001', 'bob',   'bob@example.com',   '$2y$10$Ov/91rm71cVGtsI0KqBeWuoXfcufhNLyp9JBMzixvpMHj9bFWbrtK', 'Bob Jones',  NOW(), NOW());

-- =============================================================
-- Seed: Accounts
-- alice: EUR + USD accounts
-- bob:   GBP + SEK accounts
-- =============================================================
INSERT INTO accounts (id, user_id, currency, balance, status, created_at, updated_at) VALUES
    ('a0000000-0000-0000-0001-000000000001', 'a0000000-0000-0000-0000-000000000001', 'EUR', 1000.0000, 'ACTIVE', NOW(), NOW()),
    ('a0000000-0000-0000-0002-000000000001', 'a0000000-0000-0000-0000-000000000001', 'USD',  500.0000, 'ACTIVE', NOW(), NOW()),
    ('b0000000-0000-0000-0001-000000000001', 'b0000000-0000-0000-0000-000000000001', 'GBP',  750.0000, 'ACTIVE', NOW(), NOW()),
    ('b0000000-0000-0000-0002-000000000001', 'b0000000-0000-0000-0000-000000000001', 'SEK', 5000.0000, 'ACTIVE', NOW(), NOW());

-- =============================================================
-- Seed: Exchange Rates
-- All 20 pairs for the 5 supported currencies.
-- To add rates for a new currency: INSERT rows here (new migration).
-- =============================================================
INSERT INTO exchange_rates (from_currency, to_currency, rate, effective_from, created_at) VALUES
    -- EUR base
    ('EUR', 'USD', 1.080000,     NOW(), NOW()),
    ('EUR', 'SEK', 11.500000,    NOW(), NOW()),
    ('EUR', 'GBP', 0.860000,     NOW(), NOW()),
    ('EUR', 'VND', 27000.000000, NOW(), NOW()),

    -- USD base
    ('USD', 'EUR', 0.926000,     NOW(), NOW()),
    ('USD', 'SEK', 10.650000,    NOW(), NOW()),
    ('USD', 'GBP', 0.796000,     NOW(), NOW()),
    ('USD', 'VND', 25000.000000, NOW(), NOW()),

    -- SEK base
    ('SEK', 'EUR', 0.087000,     NOW(), NOW()),
    ('SEK', 'USD', 0.094000,     NOW(), NOW()),
    ('SEK', 'GBP', 0.074700,     NOW(), NOW()),
    ('SEK', 'VND', 2350.000000,  NOW(), NOW()),

    -- GBP base
    ('GBP', 'EUR', 1.163000,     NOW(), NOW()),
    ('GBP', 'USD', 1.256000,     NOW(), NOW()),
    ('GBP', 'SEK', 13.380000,    NOW(), NOW()),
    ('GBP', 'VND', 31500.000000, NOW(), NOW()),

    -- VND base
    ('VND', 'EUR', 0.000037,     NOW(), NOW()),
    ('VND', 'USD', 0.000040,     NOW(), NOW()),
    ('VND', 'SEK', 0.000426,     NOW(), NOW()),
    ('VND', 'GBP', 0.000032,     NOW(), NOW());

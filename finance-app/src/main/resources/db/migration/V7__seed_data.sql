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
    ('VND', 'Vietnamese Dong',   TRUE)
ON CONFLICT (code) DO NOTHING;

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
    ('VND', 'GBP', 0.000032,     NOW(), NOW())
ON CONFLICT (from_currency, to_currency, effective_from) DO NOTHING;

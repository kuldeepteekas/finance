CREATE TABLE exchange_rates (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency   VARCHAR(10)   NOT NULL REFERENCES currencies(code),
    to_currency     VARCHAR(10)   NOT NULL REFERENCES currencies(code),
    rate            NUMERIC(19,6) NOT NULL,
    effective_from  TIMESTAMP     NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exchange_rates_pair_time
        UNIQUE (from_currency, to_currency, effective_from),

    -- Business rules that are always true regardless of which currencies are supported
    CONSTRAINT chk_exchange_rates_different_currencies
        CHECK (from_currency <> to_currency),
    CONSTRAINT chk_exchange_rates_positive_rate
        CHECK (rate > 0)
);

-- Efficiently fetches the latest rate for a given currency pair
CREATE INDEX idx_exchange_rates_pair
    ON exchange_rates(from_currency, to_currency, effective_from DESC);

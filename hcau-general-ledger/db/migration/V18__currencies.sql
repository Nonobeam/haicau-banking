-- ============================================================
-- V18: currencies registry
-- ============================================================
-- Introduces the `currencies` table so currency is a first-class,
-- Flyway-seeded entity instead of a bare string hardcoded to USD at
-- provisioning time. Per CoA Decision #9, currency still lives on
-- ledger_entries and balance_snapshots — this table is the registry
-- that write paths validate against, not a new column on accounts.

CREATE TABLE currencies (
    code             VARCHAR(10) PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    decimal_places   SMALLINT NOT NULL CHECK (decimal_places >= 0),
    display_decimals SMALLINT NOT NULL CHECK (display_decimals >= 0),
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT currencies_display_decimals_le_decimal_places
        CHECK (display_decimals <= decimal_places)
);

INSERT INTO currencies (code, name, decimal_places, display_decimals, is_active)
VALUES ('USD', 'US Dollar', 2, 2, true);

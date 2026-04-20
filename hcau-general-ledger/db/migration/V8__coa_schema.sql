-- ============================================================
-- V8: CoA Schema — structural DDL for the CoA redesign
-- ============================================================

-- ── 1. wallets table ──────────────────────────────────────────────────────────
CREATE TABLE wallets (
    id          VARCHAR(40) PRIMARY KEY,
    customer_id VARCHAR(40) NOT NULL REFERENCES users(id),
    is_primary  BOOLEAN     NOT NULL DEFAULT false,
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX wallets_customer_idx ON wallets(customer_id);

-- ── 2. accounts: add coa_path, ledger, wallet_id ─────────────────────────────
ALTER TABLE accounts
    ADD COLUMN coa_path  VARCHAR(255),
    ADD COLUMN ledger     VARCHAR(3) CHECK (ledger IN ('GL', 'SUB')),
    ADD COLUMN wallet_id  VARCHAR(40) REFERENCES wallets(id);

-- Make internal_coa nullable so new CoA-aware provisioning can insert without it.
-- V11 will backfill coa_path from internal_coa for existing rows.
ALTER TABLE accounts ALTER COLUMN internal_coa DROP NOT NULL;
DROP INDEX IF EXISTS accounts_internal_coa_idx;
CREATE UNIQUE INDEX accounts_internal_coa_idx ON accounts(internal_coa) WHERE internal_coa IS NOT NULL;

CREATE UNIQUE INDEX accounts_coa_path_idx ON accounts(coa_path) WHERE coa_path IS NOT NULL;
CREATE INDEX accounts_wallet_id_idx ON accounts(wallet_id);

-- Add updated_at to accounts
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- ── 3. balance_snapshots: drop and recreate with (account_id, currency) PK ───
DROP TABLE balance_snapshots;

CREATE TABLE balance_snapshots (
    account_id  VARCHAR(40)     NOT NULL REFERENCES accounts(id),
    currency    VARCHAR(10)     NOT NULL,
    balance     DECIMAL(38, 8)  NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, currency)
);

-- ── 4. ledger_entries: rename entry_type → type, amount BIGINT → DECIMAL, add currency ──
ALTER TABLE ledger_entries RENAME COLUMN entry_type TO type;

-- Cast BIGINT amount to DECIMAL(38,8).  Existing values are whole-number cents; preserve them.
ALTER TABLE ledger_entries
    ALTER COLUMN amount TYPE DECIMAL(38, 8) USING amount::DECIMAL(38, 8);

ALTER TABLE ledger_entries ADD COLUMN currency VARCHAR(10);
ALTER TABLE ledger_entries ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();

-- ── 5. transactions: rename correlation_id → trace_id, widen causation_id ────
ALTER TABLE transactions RENAME COLUMN correlation_id TO trace_id;

-- Drop self-referencing FK so causation_id can hold external provider event IDs (> 40 chars)
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_causation_id_fkey;
ALTER TABLE transactions ALTER COLUMN causation_id TYPE VARCHAR(255);

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- ── 6. updated_at on remaining tables ────────────────────────────────────────
ALTER TABLE users         ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

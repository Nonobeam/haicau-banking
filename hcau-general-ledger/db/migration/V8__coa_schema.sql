-- ==========================================================================
-- V8 — CoA Redesign: Structural Schema Changes
--
-- Adds ledger classification (GL/SUB), wallets table, restructures
-- balance_snapshots for multi-currency, aligns ledger_entries and
-- transactions columns with the new CoA model.
-- ==========================================================================

-- 1. Drop orphaned reference tables (FKs removed in V5)
DROP TABLE IF EXISTS bucket_types;
DROP TABLE IF EXISTS domain_types;

-- 2. Create wallets table
CREATE TABLE wallets (
    id          VARCHAR(40)  PRIMARY KEY,  -- UUID v7
    customer_id VARCHAR(40)  NOT NULL REFERENCES users(id),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- 3. Accounts — add ledger column, rename internal_coa → coa_path, add wallet_id FK
ALTER TABLE accounts ADD COLUMN ledger VARCHAR(3) NOT NULL DEFAULT 'SUB'
    CHECK (ledger IN ('GL', 'SUB'));

ALTER TABLE accounts RENAME COLUMN internal_coa TO coa_path;
DROP INDEX IF EXISTS accounts_internal_coa_idx;
ALTER TABLE accounts ADD CONSTRAINT accounts_coa_path_key UNIQUE (coa_path);

ALTER TABLE accounts ADD COLUMN wallet_id VARCHAR(40) REFERENCES wallets(id);

-- 4. Balance snapshots — drop and recreate with (account_id, currency) composite PK
--    Old schema: account_id PK, balance BIGINT, last_processed_entry_seq
--    New schema: (account_id, currency) PK, balance DECIMAL
DROP TABLE balance_snapshots;

CREATE TABLE balance_snapshots (
    account_id  VARCHAR(40)    NOT NULL REFERENCES accounts(id),
    currency    VARCHAR(10)    NOT NULL,
    balance     DECIMAL(38, 8) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ    DEFAULT NOW(),
    PRIMARY KEY (account_id, currency)
);

-- 5. Ledger entries — rename entry_type → type, widen amount BIGINT → DECIMAL, add currency
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_entry_type_check;
ALTER TABLE ledger_entries RENAME COLUMN entry_type TO type;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_type_check
    CHECK (type IN ('DEBIT', 'CREDIT'));

ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_amount_check;
ALTER TABLE ledger_entries ALTER COLUMN amount TYPE DECIMAL(38, 8);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_amount_positive
    CHECK (amount > 0);

ALTER TABLE ledger_entries ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'USD';

-- 6. Transactions — rework causation_id, rename correlation_id → trace_id, add PROCESSING status
--    causation_id was a self-FK to transactions(id) in V3; becomes a plain string
--    for external event IDs (e.g. Stripe payment_intent_id)
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_causation_id_fkey;
ALTER TABLE transactions ALTER COLUMN causation_id TYPE VARCHAR(255);

ALTER TABLE transactions RENAME COLUMN correlation_id TO trace_id;

-- Webhook idempotency: handled by the existing UNIQUE(idempotency_key) constraint.
-- Webhook handler sets idempotency_key = '{payment_intent_id}_{event_type}' before insert.
-- UNIQUE(causation_id, transaction_type) is NOT used because sub-tx 2's causation_id
-- points to sub-tx 1's transaction ID (internal chain), not the external event — and
-- two sub-txs can share the same transaction_type (DEPOSIT_CONFIRMED).

-- Add PROCESSING status for webhook state machine (Stripe payment_intent.processing)
ALTER TABLE transactions DROP CONSTRAINT transactions_status_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED', 'VOIDED', 'EXPIRED'));

-- ============================================================
-- V11: CoA Seed — backfill, GL system accounts, infra tables
-- ============================================================

-- ── 1. Backfill coa_path for existing customer accounts (Decision #10) ───────
-- Pass 1: mark all existing accounts as SUB (they are all customer wallet accounts).
-- This targets accounts that already have internal_coa in the old format.
-- New format: wallet:{owner_id}:{account_id}:main  (AVAILABLE bucket)
--             wallet:{owner_id}:{account_id}:reserved  (RESERVED bucket)
-- We use account id as a stand-in wallet_id for existing accounts (wallet_id FK populated below).

DO $$
DECLARE
    rec RECORD;
    new_state TEXT;
    new_coa   TEXT;
BEGIN
    FOR rec IN
        SELECT a.id, a.owner_id, a.internal_coa
        FROM accounts a
        WHERE a.coa_path IS NULL
          AND a.id != 'acct_0000000000000000000BUFFER_USD'
    LOOP
        -- Determine state from internal_coa suffix
        IF rec.internal_coa LIKE '%:AVAILABLE' THEN
            new_state := 'main';
        ELSIF rec.internal_coa LIKE '%:RESERVED' THEN
            new_state := 'reserved';
        ELSE
            new_state := 'main'; -- fallback
        END IF;

        new_coa := 'wallet:' || rec.owner_id || ':' || rec.id || ':' || new_state;

        UPDATE accounts
        SET coa_path = new_coa,
            ledger   = 'SUB',
            wallet_id = rec.id  -- temporary: use account id as wallet reference for backfill
        WHERE id = rec.id;
    END LOOP;
END $$;

-- Pass 2: create wallets rows for each backfilled SUB account group.
-- We group by owner_id and pick the 'main' account as the wallet representative.
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT DISTINCT a.owner_id
        FROM accounts a
        WHERE a.ledger = 'SUB'
          AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.customer_id = a.owner_id)
    LOOP
        -- Use the main account id as wallet id (backfill convention only)
        INSERT INTO wallets (id, customer_id, is_primary, status)
        SELECT a.id, a.owner_id, true, 'ACTIVE'
        FROM accounts a
        WHERE a.owner_id = rec.owner_id
          AND a.coa_path LIKE '%:main'
        LIMIT 1
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- Align wallet_id FK on SUB accounts to point to the correct wallets.id
UPDATE accounts a
SET wallet_id = w.id
FROM wallets w
WHERE w.customer_id = a.owner_id
  AND a.ledger = 'SUB'
  AND (a.wallet_id IS NULL OR a.wallet_id != w.id);

-- ── 2. Convert buffer account to bank:shared:main (GL) ───────────────────────
UPDATE accounts
SET coa_path  = 'bank:shared:main',
    ledger    = 'GL',
    wallet_id = NULL
WHERE id = 'acct_0000000000000000000BUFFER_USD';

-- ── 3. Insert GL system accounts ──────────────────────────────────────────────
-- Owner for all GL accounts is the SYSTEM user.
INSERT INTO accounts (id, owner_id, coa_path, ledger, status) VALUES
-- bank accounts
('acct_00000000000000BANK_SHARED_CLR', 'user_00000000000000000000000000SYSTEM', 'bank:shared:clearing', 'GL', 'ACTIVE'),
('acct_00000000000000000BANK_PROP_MN', 'user_00000000000000000000000000SYSTEM', 'bank:prop:main',       'GL', 'ACTIVE'),
('acct_00000000000000000BANK_PROP_CLR','user_00000000000000000000000000SYSTEM', 'bank:prop:clearing',   'GL', 'ACTIVE'),
-- wallet control
('acct_00000000000WALLET_CONTROL_MAIN','user_00000000000000000000000000SYSTEM', 'wallet:control:main',    'GL', 'ACTIVE'),
('acct_0000000000WALLET_CONTROL_RESV', 'user_00000000000000000000000000SYSTEM', 'wallet:control:reserved','GL', 'ACTIVE'),
('acct_0000000000WALLET_CONTROL_CLR',  'user_00000000000000000000000000SYSTEM', 'wallet:control:clearing','GL', 'ACTIVE'),
-- receivable / payable / external (stripe as default provider)
('acct_000000000RECEIVABLE_STRIPE',   'user_00000000000000000000000000SYSTEM', 'receivable:counterparty:stripe',        'GL', 'ACTIVE'),
('acct_00000RECEIVABLE_RETURN_STRIPE','user_00000000000000000000000000SYSTEM', 'receivable:counterparty:stripe:return', 'GL', 'ACTIVE'),
('acct_0000000000000PAYABLE_STRIPE',  'user_00000000000000000000000000SYSTEM', 'payable:counterparty:stripe',          'GL', 'ACTIVE'),
('acct_000000000000EXTERNAL_STRIPE',  'user_00000000000000000000000000SYSTEM', 'external:counterparty:stripe',         'GL', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- ── 4. Seed balance_snapshots for all accounts with USD (default currency) ───
INSERT INTO balance_snapshots (account_id, currency, balance)
SELECT a.id, 'USD', 0
FROM accounts a
WHERE NOT EXISTS (
    SELECT 1 FROM balance_snapshots bs WHERE bs.account_id = a.id AND bs.currency = 'USD'
)
ON CONFLICT DO NOTHING;

-- ── 5. Seed gl_snapshot_state for wallet:control:* accounts ─────────────────
INSERT INTO gl_snapshot_state (account_id, currency, last_run_at)
SELECT a.id, 'USD', NOW()
FROM accounts a
WHERE a.coa_path LIKE 'wallet:control:%'
ON CONFLICT DO NOTHING;

-- ── 6. deposit_metadata table (Decision #44) ─────────────────────────────────
CREATE TABLE deposit_metadata (
    ledger_entry_id VARCHAR(40) PRIMARY KEY REFERENCES ledger_entries(id),
    transaction_id  VARCHAR(40) NOT NULL REFERENCES transactions(id),
    account_id      VARCHAR(40) NOT NULL REFERENCES accounts(id),
    amount          DECIMAL(38, 8) NOT NULL,
    currency        VARCHAR(10) NOT NULL,
    is_settled      BOOLEAN NOT NULL DEFAULT false,
    settled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial index for efficient unsettled-deposit queries (Decision #44)
CREATE INDEX deposit_metadata_unsettled_idx
    ON deposit_metadata(currency, created_at)
    WHERE is_settled = false;

-- ── 7. wallet_provisioning_status table (Decision #47) ───────────────────────
CREATE TABLE wallet_provisioning_status (
    user_id        VARCHAR(40) PRIMARY KEY REFERENCES users(id),
    provisioned_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill: mark all existing users as provisioned (they had accounts before this migration)
INSERT INTO wallet_provisioning_status (user_id)
SELECT DISTINCT a.owner_id
FROM accounts a
JOIN users u ON u.id = a.owner_id
JOIN user_types ut ON ut.id = u.user_type
WHERE ut.name = 'USER'
ON CONFLICT DO NOTHING;

-- ── 8. Update job_config entries ──────────────────────────────────────────────
-- Add new job config keys for GL snapshot, GL purge, provisioning reconcile
INSERT INTO job_config (key, value, description) VALUES
('gl_snapshot_enabled',              'true',  'Enable the GL control account snapshot cronjob'),
('gl_snapshot_run_interval_sec',     '60',    'GL snapshot job run interval in seconds'),
('gl_purge_enabled',                 'true',  'Enable gl_processed_entries purge job'),
('gl_purge_run_interval_sec',        '600',   'GL purge job run interval in seconds'),
('gl_purge_safety_window_sec',       '300',   'Minimum age of entries before purge (must exceed overlap window)'),
('gl_purge_batch_size',              '10000', 'Max rows deleted per purge job invocation'),
('provisioning_reconcile_enabled',            'true',  'Enable wallet provisioning reconcile job'),
('provisioning_reconcile_run_interval_sec',   '300',   'Provisioning reconcile job run interval in seconds'),
('provisioning_reconcile_batch_size',         '100',   'Users processed per reconcile job run'),
('provisioning_reconcile_default_currency',   'USD',   'Default currency to provision balance snapshots for')
ON CONFLICT (key) DO NOTHING;

-- ── 9. Reconciliation indexes (Decision #43) ─────────────────────────────────
CREATE INDEX IF NOT EXISTS transactions_trace_id_idx     ON transactions(trace_id);
CREATE INDEX IF NOT EXISTS transactions_causation_id_idx ON transactions(causation_id);
CREATE INDEX IF NOT EXISTS ledger_entries_account_currency_created_idx
    ON ledger_entries(account_id, currency, created_at);

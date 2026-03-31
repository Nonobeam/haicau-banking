-- ==========================================================================
-- V11 — CoA Redesign: Backfill, GL Accounts, Balance Seeds, Indexes
--
-- Backfills existing customer accounts to new CoA paths, converts the
-- buffer account to bank:shared:main, inserts system GL accounts, seeds
-- balance_snapshots and gl_snapshot_state, and adds reconciliation indexes.
-- ==========================================================================

-- 1. Backfill existing customer wallet accounts
--    Transforms coa:v1:users:{owner}:domain:FIAT:wallet:USD:{BUCKET} paths
--    to wallet:{owner}:{wallet_id}:{state} format.
--    With no production data this is a no-op, but safe to run regardless.

CREATE TEMP TABLE _backfill_wallet_map (
    owner_id  VARCHAR(40) PRIMARY KEY,
    wallet_id VARCHAR(40) NOT NULL
);

DO $$
DECLARE
    rec         RECORD;
    v_wallet_id VARCHAR(40);
BEGIN
    -- Pass 1: AVAILABLE accounts → wallet:{owner}:{wallet_id}:main
    FOR rec IN
        SELECT id, owner_id
        FROM accounts
        WHERE coa_path LIKE 'coa:v1:users:%'
          AND coa_path LIKE '%:AVAILABLE'
    LOOP
        v_wallet_id := gen_random_uuid()::VARCHAR;

        INSERT INTO wallets (id, customer_id, is_primary, status, created_at)
        VALUES (v_wallet_id, rec.owner_id, TRUE, 'ACTIVE', NOW());

        UPDATE accounts
        SET coa_path  = 'wallet:' || rec.owner_id || ':' || v_wallet_id || ':main',
            ledger    = 'SUB',
            wallet_id = v_wallet_id
        WHERE id = rec.id;

        INSERT INTO _backfill_wallet_map (owner_id, wallet_id)
        VALUES (rec.owner_id, v_wallet_id)
        ON CONFLICT (owner_id) DO NOTHING;

        -- Seed balance snapshot for the converted main account
        INSERT INTO balance_snapshots (account_id, currency, balance)
        VALUES (rec.id, 'USD', 0);
    END LOOP;

    -- Pass 2: RESERVED accounts → wallet:{owner}:{wallet_id}:reserved
    FOR rec IN
        SELECT id, owner_id
        FROM accounts
        WHERE coa_path LIKE 'coa:v1:users:%'
          AND coa_path LIKE '%:RESERVED'
    LOOP
        SELECT wm.wallet_id INTO v_wallet_id
        FROM _backfill_wallet_map wm WHERE wm.owner_id = rec.owner_id;

        -- If no main account found (orphaned reserved), create a wallet anyway
        IF v_wallet_id IS NULL THEN
            v_wallet_id := gen_random_uuid()::VARCHAR;
            INSERT INTO wallets (id, customer_id, is_primary, status, created_at)
            VALUES (v_wallet_id, rec.owner_id, TRUE, 'ACTIVE', NOW());
            INSERT INTO _backfill_wallet_map (owner_id, wallet_id)
            VALUES (rec.owner_id, v_wallet_id);
        END IF;

        UPDATE accounts
        SET coa_path  = 'wallet:' || rec.owner_id || ':' || v_wallet_id || ':reserved',
            ledger    = 'SUB',
            wallet_id = v_wallet_id
        WHERE id = rec.id;

        INSERT INTO balance_snapshots (account_id, currency, balance)
        VALUES (rec.id, 'USD', 0);
    END LOOP;

    -- Pass 3: Create clearing accounts for each backfilled wallet (new state)
    FOR rec IN
        SELECT DISTINCT owner_id, wallet_id
        FROM _backfill_wallet_map
    LOOP
        INSERT INTO accounts (id, owner_id, coa_path, ledger, wallet_id, status, version, created_at)
        VALUES (
            gen_random_uuid()::VARCHAR,
            rec.owner_id,
            'wallet:' || rec.owner_id || ':' || rec.wallet_id || ':clearing',
            'SUB', rec.wallet_id, 'ACTIVE', 0, NOW()
        );

        -- Seed balance snapshot for the new clearing account
        INSERT INTO balance_snapshots (account_id, currency, balance)
        SELECT id, 'USD', 0 FROM accounts
        WHERE coa_path = 'wallet:' || rec.owner_id || ':' || rec.wallet_id || ':clearing';
    END LOOP;
END $$;

DROP TABLE IF EXISTS _backfill_wallet_map;

-- 2. Migrate buffer account → bank:shared:main
--    Buffer exists from V4 seed with id = acct_0000000000000000000BUFFER_USD
--    After V5+V7 its coa_path = coa:v1:users:{system}:domain:FIAT:wallet:USD:AVAILABLE
UPDATE accounts
SET coa_path = 'bank:shared:main',
    ledger   = 'GL'
WHERE id = 'acct_0000000000000000000BUFFER_USD';

-- Rename buffer account ID to match GL naming convention.
-- Drop+re-add FKs to allow the ID change (safe even with no referencing rows).
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_account_id_fkey;
ALTER TABLE balance_snapshots DROP CONSTRAINT balance_snapshots_account_id_fkey;

UPDATE accounts SET id = 'acct_0000000000000000000BANK_SHARED_MAIN'
WHERE id = 'acct_0000000000000000000BUFFER_USD';
UPDATE ledger_entries SET account_id = 'acct_0000000000000000000BANK_SHARED_MAIN'
WHERE account_id = 'acct_0000000000000000000BUFFER_USD';

ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE balance_snapshots ADD CONSTRAINT balance_snapshots_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id);

-- 3. Insert system GL accounts
--    BANK_SHARED_MAIN already exists after step 2 — not inserted here.
INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version) VALUES
('acct_00000000000000BANK_SHARED_CLEARING',  'user_00000000000000000000000000SYSTEM', 'bank:shared:clearing',           'GL', 'ACTIVE', 0),
('acct_000000000000000000BANK_PROP_MAIN',    'user_00000000000000000000000000SYSTEM', 'bank:prop:main',                 'GL', 'ACTIVE', 0),
('acct_00000000000000BANK_PROP_CLEARING',    'user_00000000000000000000000000SYSTEM', 'bank:prop:clearing',             'GL', 'ACTIVE', 0),
('acct_0000000000000EXTERNAL_STRIPE',        'user_00000000000000000000000000SYSTEM', 'external:counterparty:stripe',   'GL', 'ACTIVE', 0),
('acct_000000000000RECEIVABLE_STRIPE',       'user_00000000000000000000000000SYSTEM', 'receivable:counterparty:stripe', 'GL', 'ACTIVE', 0),
('acct_0000000000000000PAYABLE_STRIPE',      'user_00000000000000000000000000SYSTEM', 'payable:counterparty:stripe',    'GL', 'ACTIVE', 0),
('acct_00000000000WALLET_CONTROL_MAIN',      'user_00000000000000000000000000SYSTEM', 'wallet:control:main',            'GL', 'ACTIVE', 0),
('acct_0000000000WALLET_CONTROL_RESERVED',   'user_00000000000000000000000000SYSTEM', 'wallet:control:reserved',        'GL', 'ACTIVE', 0),
('acct_0000000000WALLET_CONTROL_CLEARING',   'user_00000000000000000000000000SYSTEM', 'wallet:control:clearing',        'GL', 'ACTIVE', 0);

-- 4. Seed balance_snapshots for all GL accounts (zero balances)
--    LedgerEntryWriter UPDATEs balance_snapshots; rows must exist before first entry.
INSERT INTO balance_snapshots (account_id, currency, balance) VALUES
('acct_0000000000000000000BANK_SHARED_MAIN',  'USD', 0),
('acct_00000000000000BANK_SHARED_CLEARING',   'USD', 0),
('acct_000000000000000000BANK_PROP_MAIN',     'USD', 0),
('acct_00000000000000BANK_PROP_CLEARING',     'USD', 0),
('acct_0000000000000EXTERNAL_STRIPE',         'USD', 0),
('acct_000000000000RECEIVABLE_STRIPE',        'USD', 0),
('acct_0000000000000000PAYABLE_STRIPE',       'USD', 0),
('acct_00000000000WALLET_CONTROL_MAIN',       'USD', 0),
('acct_0000000000WALLET_CONTROL_RESERVED',    'USD', 0),
('acct_0000000000WALLET_CONTROL_CLEARING',    'USD', 0);

-- 5. Seed gl_snapshot_state for wallet:control:* accounts
--    GlSnapshotJob processes only these three control accounts.
INSERT INTO gl_snapshot_state (account_id, currency, last_run_at) VALUES
('acct_00000000000WALLET_CONTROL_MAIN',     'USD', '1970-01-01'),
('acct_0000000000WALLET_CONTROL_RESERVED',  'USD', '1970-01-01'),
('acct_0000000000WALLET_CONTROL_CLEARING',  'USD', '1970-01-01');

-- 6. Deposit metadata — per-entry sweep tracking
--    Drives the sweep job query via partial index. Each DEPOSIT_CONFIRMED credit entry
--    gets a row. Sweep job sets is_swept = TRUE after processing.
CREATE TABLE deposit_metadata (
    id                VARCHAR(40) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    ledger_entry_id   VARCHAR(40) NOT NULL REFERENCES ledger_entries(id),
    transaction_id    VARCHAR(40) NOT NULL REFERENCES transactions(id),
    account_id        VARCHAR(40) NOT NULL REFERENCES accounts(id),
    amount            DECIMAL(38, 8) NOT NULL,
    currency          VARCHAR(10) NOT NULL,
    is_swept          BOOLEAN NOT NULL DEFAULT FALSE,
    swept_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Partial index: sweep job drives exclusively from unswept entries — O(small) scan
CREATE INDEX idx_unswept_deposits ON deposit_metadata(transaction_id)
    WHERE is_swept = FALSE;

-- 7. Wallet provisioning status — tracks which users have been fully provisioned
--    The provisioning reconcile job queries users LEFT JOIN this table to find gaps.
--    A row here means the user has all required accounts: wallets row, 3 SUB accounts,
--    balance_snapshots, and per-customer GL accounts (offset, revenue).
CREATE TABLE wallet_provisioning_status (
    user_id         VARCHAR(40) PRIMARY KEY REFERENCES users(id),
    wallet_id       VARCHAR(40) NOT NULL REFERENCES wallets(id),
    currency        VARCHAR(10) NOT NULL,
    provisioned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    provisioned_by  VARCHAR(40) NOT NULL  -- 'ACCOUNT_SERVICE' or 'PROVISIONING_RECONCILE_JOB'
);

-- 8. Indexes for reconciliation and sweep job performance
CREATE INDEX idx_transactions_trace_id     ON transactions(trace_id);
CREATE INDEX idx_transactions_causation_id ON transactions(causation_id);
CREATE INDEX idx_ledger_entries_account_currency_created
    ON ledger_entries(account_id, currency, created_at);
-- Note: idx_ledger_transaction (ledger_entries.transaction_id) already exists from V3

-- 8. Seed job_config for new jobs
--    All job scheduling and tuning is driven from this table at startup.
--    Columns: key, value, description (from V3 schema)
INSERT INTO job_config (key, value, description) VALUES
-- DepositSweepJob
('sweep_batch_size',              '500',   'Max rows per DB transaction in sweep job'),
('sweep_lock_timeout_ms',         '5000',  'Lock timeout per batch in sweep job'),
-- GlSnapshotJob
('gl_snapshot_run_interval_sec',  '60',    'How often GlSnapshotJob runs (seconds)'),
('gl_snapshot_overlap_window_sec','60',    'Backward overlap window for ledger_entries polling (seconds)'),
-- GlProcessedEntriesPurgeJob
('gl_purge_enabled',              'true',  'Enable gl_processed_entries purge job'),
('gl_purge_run_interval_sec',     '600',   'How often purge job runs (seconds)'),
('gl_purge_safety_window_sec',    '300',   'Entries older than this are eligible for purge (must be > overlap window)'),
('gl_purge_batch_size',           '10000', 'Max rows deleted per batch in purge job'),
-- ReconciliationJob
('recon_run_interval_sec',        '3600',  'How often reconciliation checks run (seconds)'),
('recon_stale_threshold_hours',   '72',    'Standard trace_id staleness threshold (hours)'),
('recon_late_reversal_stale_days','14',    'WITHDRAWAL_LATE_REVERSAL staleness threshold (days)'),
-- ProvisioningReconcileJob
('provisioning_reconcile_enabled',       'true', 'Enable wallet provisioning reconcile job'),
('provisioning_reconcile_run_interval_sec','300', 'How often provisioning reconcile job runs (seconds)'),
('provisioning_reconcile_batch_size',    '100',  'Max users to provision per job run'),
('provisioning_reconcile_default_currency','USD', 'Default currency for auto-provisioned wallets')
ON CONFLICT (key) DO NOTHING;

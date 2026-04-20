-- ============================================================
-- V10: CoA Infrastructure — transaction types, GL snapshot tables
-- ============================================================

-- ── 1. Widen transaction_types.name ──────────────────────────────────────────
ALTER TABLE transaction_types ALTER COLUMN name TYPE VARCHAR(60);

-- ── 2. Add gl_required and sub_required to transaction_types (Decision #33) ──
ALTER TABLE transaction_types
    ADD COLUMN gl_required  BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN sub_required VARCHAR(20);

-- Back-fill existing types as GL-exempt (they pre-date the GL/SUB split)
UPDATE transaction_types SET gl_required = false WHERE name IN ('INBOUND_DEPOSIT', 'INBOUND_SWEEP');

-- ── 3. Insert new transaction types ──────────────────────────────────────────
INSERT INTO transaction_types (id, name, description, gl_required, sub_required) VALUES
-- Deposit lifecycle
('ttype_000000000DEPOSIT_RECEIVABLE', 'DEPOSIT_RECEIVABLE',
    'Customer initiates deposit: GL records receivable + clearing liability; SUB records customer clearing',
    true, 'CLEARING'),
('ttype_0000000000DEPOSIT_CONFIRMED', 'DEPOSIT_CONFIRMED',
    'Stripe confirms payment: GL closes receivable, opens external; SUB moves clearing -> main',
    true, 'CLEARING,MAIN'),
('ttype_00000000000DEPOSIT_REVERSAL', 'DEPOSIT_REVERSAL',
    'Deposit failed/expired before confirmation: GL releases clearing; SUB reverses customer clearing',
    true, 'CLEARING'),
('ttype_000000000000BANK_SETTLEMENT', 'BANK_SETTLEMENT',
    'Platform treasury: funds arrive in platform bank from provider payout',
    true, null),

-- Withdrawal lifecycle
('ttype_0000000000WITHDRAWAL_INITIATE', 'WITHDRAWAL_INITIATE',
    'Customer initiates withdrawal: main -> reserved',
    true, 'MAIN,RESERVED'),
('ttype_00000000WITHDRAWAL_PROVIDER_SENT', 'WITHDRAWAL_PROVIDER_SENT',
    'Withdrawal sent to provider API: reserved -> clearing',
    true, 'RESERVED,CLEARING'),
('ttype_00000000000WITHDRAWAL_CONFIRMED', 'WITHDRAWAL_CONFIRMED',
    'Provider confirms withdrawal: clearing exits SUB, opens payable GL',
    true, 'CLEARING'),
('ttype_0000000000000WITHDRAWAL_SETTLED', 'WITHDRAWAL_SETTLED',
    'Bank settlement: payable closed, bank decreases',
    true, null),
('ttype_0000WITHDRAWAL_LATE_REVERSAL', 'WITHDRAWAL_LATE_REVERSAL',
    'Provider returns funds after settlement: customer wallet restored',
    true, 'MAIN'),
('ttype_000WITHDRAWAL_RETURN_SETTLED', 'WITHDRAWAL_RETURN_SETTLED',
    'Bank receives returned withdrawal funds: receivable:return closed',
    true, null),

-- Internal transfer
('ttype_0000INTERNAL_TRANSFER_INSTANT', 'INTERNAL_TRANSFER_INSTANT',
    'Instant same-vault transfer: SUB only, no GL (aggregate unchanged)',
    false, 'MAIN'),
('ttype_00000INTERNAL_TRANSFER_GATED', 'INTERNAL_TRANSFER_GATED',
    'Staged transfer with compliance gate: GL + SUB at each transition',
    true, 'MAIN'),
('ttype_00000GATED_TRANSFER_REVERSAL', 'GATED_TRANSFER_REVERSAL',
    'Reversal of a gated transfer',
    true, 'MAIN'),

-- Fee
('ttype_000000000000FEE_COLLECTION', 'FEE_COLLECTION',
    'Platform fee collected from customer wallet',
    true, 'MAIN');

-- ── 4. gl_snapshot_state table ───────────────────────────────────────────────
-- Tracks the last-processed timestamp per (control account, currency) for GlSnapshotJob.
CREATE TABLE gl_snapshot_state (
    account_id  VARCHAR(40)  NOT NULL REFERENCES accounts(id),
    currency    VARCHAR(10)  NOT NULL,
    last_run_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, currency)
);

-- ── 5. gl_processed_entries table ────────────────────────────────────────────
-- Dedup table used by the overlap-window GlSnapshotJob (Decision #42).
CREATE TABLE gl_processed_entries (
    ledger_entry_id VARCHAR(40) NOT NULL REFERENCES ledger_entries(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (ledger_entry_id)
);

CREATE INDEX gl_processed_entries_created_at_idx ON gl_processed_entries(created_at);

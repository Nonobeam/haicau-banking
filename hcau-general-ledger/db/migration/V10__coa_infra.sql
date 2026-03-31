-- ==========================================================================
-- V10 — CoA Redesign: Transaction Types & GL Snapshot Infrastructure
--
-- Widens transaction_types.name for longer type names, adds classification
-- columns (gl_required, sub_required), inserts all new transaction types,
-- and creates GL snapshot state tables for the overlap-window polling approach.
-- ==========================================================================

-- 1. Widen transaction_types.name (current VARCHAR(20) too short for new names)
ALTER TABLE transaction_types ALTER COLUMN name TYPE VARCHAR(40);

-- 2. Add classification columns used by reconciliation checks
--    gl_required: whether the transaction type must produce GL entries
--    sub_required: '(a)' = cross-ledger single-sided, '(b)' = intra-wallet self-balancing,
--                  'yes' = both sides required, 'no' = GL-only (no SUB entries)
ALTER TABLE transaction_types ADD COLUMN gl_required  BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE transaction_types ADD COLUMN sub_required VARCHAR(10)
    CHECK (sub_required IN ('(a)', '(b)', 'yes', 'no'));

-- 3. Classify existing (legacy) transaction types
--    These predate the GL/SUB model. Kept for historical ledger_entries references.
UPDATE transaction_types SET gl_required = FALSE, sub_required = NULL
WHERE name IN ('INBOUND_DEPOSIT', 'INBOUND_SWEEP');

-- 4. Insert new transaction types with classification
INSERT INTO transaction_types (id, name, description, gl_required, sub_required) VALUES
-- Deposit flow
('ttype_0000000000000DEPOSIT_RECEIVABLE',  'DEPOSIT_RECEIVABLE',  'Stage 1 — records receivable claim and customer reserved entry',                           TRUE,  '(a)'),
('ttype_00000000000000DEPOSIT_CONFIRMED',  'DEPOSIT_CONFIRMED',   'Stage 3 — two sub-transactions on webhook COMPLETED, linked by causation_id',              TRUE,  '(b)'),
('ttype_000000000000000000DEPOSIT_SWEEP',  'DEPOSIT_SWEEP',       'Stage 4 — sweep job moves wallet:clearing to wallet:main',                                 TRUE,  '(b)'),
('ttype_0000000000000000DEPOSIT_REVERSAL', 'DEPOSIT_REVERSAL',    'Deposit failure/reversal before COMPLETED — reverses Stage 1 entries',                      TRUE,  '(a)'),
('ttype_000000000000000BANK_SETTLEMENT',   'BANK_SETTLEMENT',     'Stage N — provider payout sweeps confirmed funds from external to bank:shared:main',        TRUE,  'no'),
-- Withdrawal flow
('ttype_00000000000WITHDRAWAL_INITIATE',   'WITHDRAWAL_INITIATE',       'W-Stage 1 — customer initiates, wallet:main locked to wallet:reserved',               TRUE,  '(b)'),
('ttype_000000WITHDRAWAL_PROVIDER_SENT',   'WITHDRAWAL_PROVIDER_SENT',  'W-Stage 2 — provider API called, wallet:reserved moves to wallet:clearing',            TRUE,  '(b)'),
('ttype_0000000000WITHDRAWAL_CONFIRMED',   'WITHDRAWAL_CONFIRMED',      'W-Stage 3 — provider confirms, wallet:clearing exits SUB, payable opened',             TRUE,  '(a)'),
('ttype_00000000000WITHDRAWAL_SETTLED',    'WITHDRAWAL_SETTLED',        'W-Stage 4 — bank settlement, payable closed, bank:shared:main decreases',              TRUE,  'no'),
('ttype_000WITHDRAWAL_LATE_REVERSAL',      'WITHDRAWAL_LATE_REVERSAL',  'Late reversal — funds re-enter SUB world (Variant A or B Phase 1)',                     TRUE,  '(a)'),
('ttype_0000WITHDRAWAL_RETURN_SETTLED',    'WITHDRAWAL_RETURN_SETTLED', 'Variant B Phase 2 — bank return arrives, receivable settled',                           TRUE,  'no'),
-- Internal transfers
('ttype_00000INTERNAL_TRANSFER_INSTANT',   'INTERNAL_TRANSFER_INSTANT', 'Instant same-vault transfer, SUB only, no GL entries',                                  FALSE, '(b)'),
('ttype_000000INTERNAL_TRANSFER_GATED',    'INTERNAL_TRANSFER_GATED',   'Staged transfer through reserved/clearing, GL+SUB entries required',                    TRUE,  '(b)'),
('ttype_000000GATED_TRANSFER_REVERSAL',    'GATED_TRANSFER_REVERSAL',   'Reversal of staged internal transfer from reserved or clearing back to main',           TRUE,  '(b)'),
-- Fee
('ttype_00000000000000000FEE_COLLECTION',  'FEE_COLLECTION',            'Fee collected from customer wallet, offset cleared to revenue',                          TRUE,  'yes');

-- 5. GL snapshot state tables (overlap-window polling, not seq-based cursor)
--    gl_snapshot_state: tracks last_run_at per (account_id, currency) for the cronjob
--    gl_processed_entries: dedup log to prevent double-counting in the overlap window
CREATE TABLE gl_snapshot_state (
    account_id   VARCHAR(40)  NOT NULL,
    currency     VARCHAR(10)  NOT NULL,
    last_run_at  TIMESTAMPTZ  NOT NULL DEFAULT '1970-01-01',
    PRIMARY KEY (account_id, currency)
);

CREATE TABLE gl_processed_entries (
    ledger_entry_id  VARCHAR(40)  PRIMARY KEY
);

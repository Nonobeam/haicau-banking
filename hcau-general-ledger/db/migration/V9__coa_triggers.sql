-- ==========================================================================
-- V9 — CoA Redesign: Trigger Updates
--
-- Updates the V2 balance-check trigger for the GL/SUB ledger model.
-- After V8 renamed entry_type → type, the trigger function body must be
-- rewritten. Also changes from whole-transaction balance check to GL-only
-- balance check, because cross-ledger SUB entries (class a) intentionally
-- do not self-balance within a single transaction.
-- ==========================================================================

-- Recreate the balance-check function.
-- Only GL ledger entries must balance per transaction. SUB detail entries
-- may be single-sided (class a: cross-ledger) or self-balancing (class b:
-- intra-wallet). GL balance is the authoritative integrity check.
--
-- For GL-exempt transactions (e.g. INTERNAL_TRANSFER_INSTANT), no GL entries
-- exist — SUM evaluates to 0 = 0, which passes.
CREATE OR REPLACE FUNCTION check_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_credit DECIMAL(38, 8);
    total_debit  DECIMAL(38, 8);
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        SELECT
            COALESCE(SUM(le.amount) FILTER (WHERE le.type = 'CREDIT'), 0),
            COALESCE(SUM(le.amount) FILTER (WHERE le.type = 'DEBIT'), 0)
        INTO total_credit, total_debit
        FROM ledger_entries le
        JOIN accounts a ON le.account_id = a.id
        WHERE le.transaction_id = NEW.id
          AND a.ledger = 'GL';

        IF total_credit != total_debit THEN
            RAISE EXCEPTION 'transaction % GL entries do not balance: credit=% debit=%',
                NEW.id, total_credit, total_debit;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger itself (trg_check_transaction_balance) is unchanged — it already
-- references check_transaction_balance() and fires BEFORE UPDATE on transactions.

-- check_account_active trigger (V2) is unchanged — still validates account
-- status before ledger entry insert. LedgerEntryWriter adds wallet-level
-- validation as a separate concern.

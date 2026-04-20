-- ============================================================
-- V9: CoA Triggers — rewrite check_transaction_balance for new schema
-- ============================================================
-- The previous trigger (V2/V3) checked ALL entries.
-- Post-CoA, SUB entries do not self-balance per transaction (cross-ledger class-a entries).
-- The trigger now checks GL entries only; the GL must always balance.

CREATE OR REPLACE FUNCTION check_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_credit DECIMAL(38, 8);
    total_debit  DECIMAL(38, 8);
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        SELECT
            COALESCE(SUM(le.amount) FILTER (WHERE le.type = 'CREDIT'), 0),
            COALESCE(SUM(le.amount) FILTER (WHERE le.type = 'DEBIT'),  0)
        INTO total_credit, total_debit
        FROM ledger_entries le
        JOIN accounts a ON le.account_id = a.id
        WHERE le.transaction_id = NEW.id
          AND a.ledger = 'GL';

        IF total_credit != total_debit THEN
            RAISE EXCEPTION 'GL entries for transaction % do not balance: credit=% debit=%',
                NEW.id, total_credit, total_debit;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
-- Trigger already exists from V2; CREATE OR REPLACE updates the function in place.

-- ==========================================
-- 1. OCC Version Column on Accounts
-- ==========================================
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ==========================================
-- 2. Balance Enforcement Trigger
-- Fires BEFORE UPDATE on transactions when
-- status transitions to COMPLETED.
-- Rejects if sum(credits) != sum(debits).
-- ==========================================
CREATE OR REPLACE FUNCTION check_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_credit DECIMAL(38, 8);
    total_debit  DECIMAL(38, 8);
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        SELECT
            COALESCE(SUM(credit), 0),
            COALESCE(SUM(debit), 0)
        INTO total_credit, total_debit
        FROM ledger_entries
        WHERE transaction_id = NEW.id;

        IF total_credit != total_debit THEN
            RAISE EXCEPTION 'transaction % entries do not balance: credit=% debit=%',
                NEW.id, total_credit, total_debit;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_transaction_balance
BEFORE UPDATE ON transactions
FOR EACH ROW EXECUTE FUNCTION check_transaction_balance();

-- ==========================================
-- 3. Account Validity Trigger
-- Fires BEFORE INSERT on ledger_entries.
-- Rejects if any referenced account is not ACTIVE.
-- ==========================================
CREATE OR REPLACE FUNCTION check_account_active()
RETURNS TRIGGER AS $$
DECLARE
    acct_status VARCHAR(20);
BEGIN
    SELECT status INTO acct_status FROM accounts WHERE id = NEW.account_id;
    IF acct_status IS NULL THEN
        RAISE EXCEPTION 'account % does not exist', NEW.account_id;
    END IF;
    IF acct_status != 'ACTIVE' THEN
        RAISE EXCEPTION 'account % is not ACTIVE (status=%)', NEW.account_id, acct_status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_account_active
BEFORE INSERT ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION check_account_active();

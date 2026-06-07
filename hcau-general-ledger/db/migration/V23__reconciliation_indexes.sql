CREATE INDEX IF NOT EXISTS idx_ledger_account_currency_created
    ON ledger_entries (account_id, created_at DESC);

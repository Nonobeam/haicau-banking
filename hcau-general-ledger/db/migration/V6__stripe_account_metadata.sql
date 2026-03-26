ALTER TABLE accounts
    ADD COLUMN stripe_account_id VARCHAR(64),
    ADD COLUMN stripe_metadata JSONB;

CREATE INDEX IF NOT EXISTS accounts_stripe_account_id_idx
    ON accounts(stripe_account_id);

-- 1. Balance snapshots restructure (drop and recreate)
DROP TABLE balance_snapshots;

-- 2. Drop Foreign Keys & Constraints for PK/FK type change
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_account_id_fkey;
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_transaction_id_fkey;
ALTER TABLE transactions DROP CONSTRAINT transactions_transaction_type_fkey;
ALTER TABLE accounts DROP CONSTRAINT accounts_bucket_type_fkey;
ALTER TABLE accounts DROP CONSTRAINT accounts_domain_fkey;
ALTER TABLE accounts DROP CONSTRAINT accounts_owner_id_fkey;
ALTER TABLE users DROP CONSTRAINT users_user_type_fkey;

-- 3. Alter Column Types from UUID to VARCHAR(40) AND remove gen_random_uuid
ALTER TABLE user_types ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40);
ALTER TABLE users ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40), ALTER COLUMN user_type TYPE VARCHAR(40);
ALTER TABLE bucket_types ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40);
ALTER TABLE domain_types ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40);
ALTER TABLE accounts ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40), ALTER COLUMN owner_id TYPE VARCHAR(40), ALTER COLUMN domain TYPE VARCHAR(40), ALTER COLUMN bucket_type TYPE VARCHAR(40);
ALTER TABLE transaction_types ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40);
ALTER TABLE transactions ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40), ALTER COLUMN transaction_type TYPE VARCHAR(40);
ALTER TABLE ledger_entries ALTER COLUMN id DROP DEFAULT, ALTER COLUMN id TYPE VARCHAR(40), ALTER COLUMN transaction_id TYPE VARCHAR(40), ALTER COLUMN account_id TYPE VARCHAR(40);

-- 4. Re-add Foreign Keys
ALTER TABLE users ADD CONSTRAINT users_user_type_fkey FOREIGN KEY (user_type) REFERENCES user_types(id);
ALTER TABLE accounts ADD CONSTRAINT accounts_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);
ALTER TABLE accounts ADD CONSTRAINT accounts_domain_fkey FOREIGN KEY (domain) REFERENCES domain_types(id);
ALTER TABLE accounts ADD CONSTRAINT accounts_bucket_type_fkey FOREIGN KEY (bucket_type) REFERENCES bucket_types(id);
ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_fkey FOREIGN KEY (transaction_type) REFERENCES transaction_types(id);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES transactions(id);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id);

-- 5. Ledger entry restructure
ALTER TABLE ledger_entries DROP CONSTRAINT one_side_only;
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_credit_check;
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_debit_check;
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_account_id_transaction_id_key;
ALTER TABLE ledger_entries DROP COLUMN credit;
ALTER TABLE ledger_entries DROP COLUMN debit;
ALTER TABLE ledger_entries ADD COLUMN amount BIGINT NOT NULL CHECK (amount > 0);
ALTER TABLE ledger_entries ADD COLUMN entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('CREDIT', 'DEBIT'));

-- 6. Update V2 Triggers
CREATE OR REPLACE FUNCTION check_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_credit BIGINT;
    total_debit  BIGINT;
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        SELECT
            COALESCE(SUM(amount) FILTER (WHERE entry_type = 'CREDIT'), 0),
            COALESCE(SUM(amount) FILTER (WHERE entry_type = 'DEBIT'), 0)
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

-- 7. Bucket type stays ACCOUNTED/RESERVED by project convention

-- 8. External Providers Table (needed for transactions FK)
CREATE TABLE external_providers (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(60) UNIQUE NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PAYMENT_PROCESSOR', 'BANK', 'CRYPTO_NETWORK')),
        config JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 9. Transaction table extensions
ALTER TABLE transactions 
    ADD COLUMN actor_id VARCHAR(40) REFERENCES users(id),
    ADD COLUMN correlation_id VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN causation_id VARCHAR(40) REFERENCES transactions(id),
    ADD COLUMN source_service VARCHAR(60),
    ADD COLUMN provider_id VARCHAR(40) REFERENCES external_providers(id);
ALTER TABLE transactions ALTER COLUMN correlation_id DROP DEFAULT;

ALTER TABLE transactions DROP CONSTRAINT transactions_status_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_status_check CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED', 'VOIDED', 'EXPIRED'));

-- 10. Recreate Balance Snapshots
CREATE TABLE balance_snapshots (
    account_id VARCHAR(40) PRIMARY KEY REFERENCES accounts(id),
    balance BIGINT NOT NULL DEFAULT 0,
    last_processed_entry_seq BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 11. New Tables
CREATE TABLE job_config (
    key VARCHAR(60) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE idempotency_config (
    key VARCHAR(60) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE job_tracking (
    job_name VARCHAR(60) NOT NULL,
    transaction_id VARCHAR(40) NOT NULL REFERENCES transactions(id),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    skipped_reason VARCHAR(255),
    processed_at TIMESTAMPTZ,
    PRIMARY KEY (job_name, transaction_id)
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    response_status INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

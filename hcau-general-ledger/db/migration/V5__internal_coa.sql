-- Add internal_coa column and backfill
ALTER TABLE accounts ADD COLUMN internal_coa VARCHAR(255);

-- Populate internal_coa using existing columns
UPDATE accounts a
SET internal_coa = CONCAT(
    'coa:v1:users:', a.owner_id,
    ':domain:', dt.name,
    ':wallet:', a.currency,
    ':', bt.name
  )
FROM domain_types dt, bucket_types bt
WHERE a.domain = dt.id AND a.bucket_type = bt.id;

-- Enforce NOT NULL and uniqueness
ALTER TABLE accounts ALTER COLUMN internal_coa SET NOT NULL;
CREATE UNIQUE INDEX accounts_internal_coa_idx ON accounts(internal_coa);

-- Drop old constraints and columns no longer needed
ALTER TABLE accounts DROP CONSTRAINT accounts_domain_fkey;
ALTER TABLE accounts DROP CONSTRAINT accounts_bucket_type_fkey;
ALTER TABLE accounts DROP COLUMN domain;
ALTER TABLE accounts DROP COLUMN currency;
ALTER TABLE accounts DROP COLUMN bucket_type;

-- Optional: drop lookup tables if unused elsewhere
-- DROP TABLE bucket_types;
-- DROP TABLE domain_types;

# Plan: Chart of Accounts Redesign

## Overview

Replace the current `internal_coa` path format and dual-bucket model with the full CoA path structure
defined in the design spec. Introduce explicit account types (bank, wallet, receivable, offset,
revenue, external), three wallet states (main, reserved, clearing), GL vs SUB ledger distinction,
and system-owned boundary accounts to support the complete deposit flow.

---

## Current State

| Aspect             | Current                                                             |
|--------------------|---------------------------------------------------------------------|
| CoA path format    | `users:{uuid}:domain:FIAT:wallet:{currency}:{BUCKET}`               |
| Account states     | `AVAILABLE`, `RESERVED` (BucketEnum)                                |
| Account types      | Implicit — all accounts are wallet-like                             |
| Ledger distinction | None — single table, no GL/SUB concept                              |
| System buffer      | `acct_0000000000000000000BUFFER_USD` — SYSTEM user, AVAILABLE       |
| Reference tables   | `domain_types`, `bucket_types` in DB                                |
| CoA model location | Duplicate: `hcau-general-ledger` only, not in `hcau-banking-common` |

### Current deposit flow entries

| Entry | Account | Type |
|---|---|---|
| Deposit initiated | `wallet:user:USD:RESERVED` | CREDIT |
| Deposit initiated | system buffer (AVAILABLE) | DEBIT |
| Webhook COMPLETED | (transaction marked COMPLETED) | — |
| Sweep job | moves RESERVED → AVAILABLE | — |

---

## Target State

### CoA path format

```
bank:shared:main                              -- shared fiat pool, confirmed
bank:shared:clearing                          -- shared fiat pool, in-transit
bank:prop:main                                -- platform proprietary, confirmed
bank:prop:clearing                            -- platform proprietary, in-transit
wallet:{customer_id}:{wallet_id}:main         -- customer spendable balance
wallet:{customer_id}:{wallet_id}:reserved     -- earmarked, operation not yet fired
wallet:{customer_id}:{wallet_id}:clearing     -- in-flight, waiting confirmation
receivable:counterparty:{provider}            -- claim owed to platform, not yet settled
offset:segregated:{customer_id}:paid_fee      -- fee bridging offset
revenue:segregated:{customer_id}:fee          -- earned fee revenue
external:counterparty:{provider}              -- boundary account at provider edge (non-deposit flows)
```

### Wallet states

| State      | Meaning                                     |
|------------|---------------------------------------------|
| `main`     | Available, spendable. Replaces `AVAILABLE`. |
| `reserved` | Locked before operation fires. Keeps name.  |
| `clearing` | In-flight, waiting on confirmation. New.    |

### Ledger distinction

Each account row carries a `ledger` column: `GL` or `SUB`.

- `GL` — aggregate control total, platform-wide.
- `SUB` — per-customer detail. Balances must roll up to GL.

Customer wallet accounts live primarily in `SUB`. Bank, receivable, offset, revenue, and external
accounts live primarily in `GL`. GL mirror rows for wallet accounts are created at provisioning time.

---

## Resolved Decisions

| # | Decision | Resolution |
|---|---|---|
| 1 | GL mirror rows for wallet accounts | Create at provisioning time |
| 2 | DEPOSIT_CONFIRMED transaction structure | Two transactions linked by `trace_id` (internal correlation). Each carries a `causation_id` referencing the external event (e.g., Stripe event ID) for reconciliation. |
| 3 | receivable and external accounts | Seed at startup — new providers require a migration |
| 4 | wallet_id convention | UUID v7 — independent ID generated at provisioning, not derived from any account row. All IDs will migrate to UUID v7 in a future pass; wallet_id follows the same convention from the start. |
| 5 | BucketEnum during migration | Kept temporarily as alias, deleted once both services migrated |
| 6 | external vs receivable in deposit flow | `receivable:counterparty:{provider}` is used at Stage 1 to record the in-flight claim before settlement. `external:counterparty:{provider}` is NOT used in the deposit flow — it belongs to other boundary flows. |
| 7 | wallet:clearing for deposits | Keep — provides rollback safety and auditability between webhook COMPLETED and sweep |
| 8 | AccountResponse API change | Internal only — breaking change is acceptable, no deprecation strategy needed |
| 9 | currency column on accounts | Drop from `accounts`. Add to `ledger_entries` (per entry) and change `balance_snapshots` PK to `(account_id, currency)`. |
| 10 | Backfill atomicity | Write two-pass backfill as PL/pgSQL inline in V8 — keeps migration self-contained and atomic under Flyway. |

---

## Deposit Flow (Authoritative)

### Stage 1 — Customer initiates deposit

Stripe has not confirmed anything. The platform records a receivable claim against Stripe and
earmarks the customer balance.

| Account                                     | Entry  | Meaning                                           |
|---------------------------------------------|--------|---------------------------------------------------|
| `receivable:counterparty:stripe`            | DEBIT  | Claim on Stripe recorded — money not yet received |
| `wallet:{customer_id}:{wallet_id}:reserved` | CREDIT | Customer funds earmarked                          |

### Stage 2 — Stripe payment initiated webhook

No ledger entries. Transaction status updated only. Stripe has acknowledged the payment is in
progress on their side but nothing has settled yet.

### Stage 3 — Stripe webhook COMPLETED

Two transactions linked by `causation_id`. Both posted in response to the COMPLETED webhook.

**Sub-tx 1 — bank side confirmed:**

| Account | Entry | Meaning |
|---|---|---|
| `bank:shared:main` | DEBIT | Money confirmed landed |
| `receivable:counterparty:stripe` | CREDIT | Receivable claim settled |

**Sub-tx 2 — customer wallet moves forward (`causation_id` → Sub-tx 1):**

| Account | Entry | Meaning |
|---|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT | Release earmark |
| `wallet:{customer_id}:{wallet_id}:clearing` | CREDIT | Move to in-flight |

### Stage 4 — Sweep job

| Account | Entry | Meaning |
|---|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | DEBIT | Release in-flight |
| `wallet:{customer_id}:{wallet_id}:main` | CREDIT | Customer balance spendable |

### Failure / Reversal (any stage before COMPLETED)

| Account | Entry | Meaning |
|---|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT | Release earmark |
| `receivable:counterparty:stripe` | CREDIT | Cancel claim |

---

## Migration Strategy

### Phase 1 — CoA path model in `hcau-banking-common`

Move CoA logic from `hcau-general-ledger` into the shared module so both services use it.

**New classes:**

- `AccountType` enum: `BANK`, `WALLET`, `RECEIVABLE`, `OFFSET`, `REVENUE`, `EXTERNAL`
- `AccountState` enum: `MAIN`, `RESERVED`, `CLEARING`, `PAID_FEE`, `FEE`
- `LedgerType` enum: `GL`, `SUB`
- `WalletId` value type: wraps UUID v7 string. Used as `wallet_id` in CoA paths.
- `CoaPath` record: replaces `InternalCoa`. Holds parsed path segments.
- `CoaPathParser` utility: replaces `InternalCoaFactory`. Builds, parses, and validates CoA paths.
  Validation here is the only gate against invalid paths reaching the DB.

**BucketEnum migration:**
- `AVAILABLE` → `AccountState.MAIN`
- `RESERVED` → `AccountState.RESERVED`
- `BucketEnum` is kept temporarily as an alias during migration. Deleted once both services migrate.

**CoaPath format rules:**

| Account type | Path segments |
|---|---|
| bank | `bank:{shared\|prop}:{main\|clearing}` |
| wallet | `wallet:{customer_id}:{wallet_id}:{main\|reserved\|clearing}` |
| receivable | `receivable:counterparty:{provider}` |
| offset | `offset:segregated:{customer_id}:paid_fee` |
| revenue | `revenue:segregated:{customer_id}:fee` |
| external | `external:counterparty:{provider}` |

**wallet_id convention:** UUID v7 generated independently at wallet provisioning time. Not derived
from any account row ID. Shared across the `main`, `reserved`, and `clearing` account rows for that
wallet. All IDs in the system will migrate to UUID v7 in a future pass — wallet_id uses UUID v7 from
the start to avoid a second migration.

**Files to create/change in `hcau-banking-common`:**
- `src/main/java/per/nonobeam/common/account/AccountType.java` (new enum)
- `src/main/java/per/nonobeam/common/account/AccountState.java` (new enum, replaces BucketEnum)
- `src/main/java/per/nonobeam/common/account/LedgerType.java` (new enum)
- `src/main/java/per/nonobeam/common/account/WalletId.java` (new value type — UUID v7)
- `src/main/java/per/nonobeam/common/account/CoaPath.java` (new record)
- `src/main/java/per/nonobeam/common/account/CoaPathParser.java` (new utility)
- `src/main/java/per/nonobeam/common/account/BucketEnum.java` (delete after migration)
- `src/main/java/per/nonobeam/common/account/Account.java` (add `ledger` field, rename `internalCoa` → `coaPath`, remove `currency`)
- `src/main/java/per/nonobeam/repository/AccountRepository.java` (update query methods)

---

### Phase 2 — Database migration (next version after V7)

**V8__coa_redesign.sql**

```sql
-- 1. Add ledger column to accounts
ALTER TABLE accounts ADD COLUMN ledger VARCHAR(3) NOT NULL DEFAULT 'SUB'
    CHECK (ledger IN ('GL', 'SUB'));

-- 2. Rename internal_coa → coa_path
ALTER TABLE accounts RENAME COLUMN internal_coa TO coa_path;

-- 3. Drop old unique constraint, add new composite unique constraint
ALTER TABLE accounts DROP CONSTRAINT accounts_internal_coa_key;
ALTER TABLE accounts ADD CONSTRAINT accounts_coa_path_ledger_key UNIQUE (coa_path, ledger);

-- 4. Drop FK constraints and columns replaced by coa_path
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_domain_fkey;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_bucket_type_fkey;
ALTER TABLE accounts DROP COLUMN IF EXISTS domain;
ALTER TABLE accounts DROP COLUMN IF EXISTS bucket_type;
ALTER TABLE accounts DROP COLUMN IF EXISTS currency;

-- 5. Drop reference tables no longer needed
DROP TABLE IF EXISTS domain_types;
DROP TABLE IF EXISTS bucket_types;

-- 6. Add currency to ledger_entries
ALTER TABLE ledger_entries ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'USD';

-- 7. Update balance_snapshots — drop old PK, add currency, new composite PK
ALTER TABLE balance_snapshots DROP CONSTRAINT balance_snapshots_pkey;
ALTER TABLE balance_snapshots ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'USD';
ALTER TABLE balance_snapshots ADD PRIMARY KEY (account_id, currency);

-- 8. Add wallets table
CREATE TABLE wallets (
    id          VARCHAR(40)  PRIMARY KEY,  -- UUID v7
    customer_id VARCHAR(40)  NOT NULL REFERENCES users(id),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- 9. Create temporary backfill map table (must exist before DO block)
CREATE TEMP TABLE _backfill_wallet_map (
    owner_id  VARCHAR(40) PRIMARY KEY,
    wallet_id VARCHAR(40) NOT NULL
);

-- 10. Backfill: migrate existing customer wallet paths (PL/pgSQL — inline, atomic)
--     NOTE: gen_random_uuid() produces UUID v4, not v7. Backfilled wallet_ids will be v4.
--     New wallets created from Java will use UUID v7. This is acceptable for migration.
DO $$
DECLARE
    rec         RECORD;
    wallet_id   VARCHAR(40);
BEGIN
    -- Pass 1: AVAILABLE accounts → wallet:{owner}:{wallet_id}:main
    -- Generate a wallet_id per owner and update the path.
    FOR rec IN
        SELECT id, owner_id
        FROM accounts
        WHERE coa_path LIKE 'users:%'
          AND coa_path LIKE '%:AVAILABLE'
    LOOP
        wallet_id := gen_random_uuid()::VARCHAR;

        UPDATE accounts
        SET coa_path = 'wallet:' || rec.owner_id || ':' || wallet_id || ':main',
            ledger   = 'SUB'
        WHERE id = rec.id;

        -- Insert GL mirror row
        INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version, created_at)
        SELECT gen_random_uuid()::VARCHAR, owner_id,
               'wallet:' || rec.owner_id || ':' || wallet_id || ':main',
               'GL', status, 0, NOW()
        FROM accounts WHERE id = rec.id;

        -- Persist mapping for pass 2
        INSERT INTO _backfill_wallet_map (owner_id, wallet_id) VALUES (rec.owner_id, wallet_id)
        ON CONFLICT (owner_id) DO NOTHING;
    END LOOP;

    -- Pass 2: RESERVED accounts → wallet:{owner}:{wallet_id}:reserved
    FOR rec IN
        SELECT id, owner_id
        FROM accounts
        WHERE coa_path LIKE 'users:%'
          AND coa_path LIKE '%:RESERVED'
    LOOP
        SELECT wm.wallet_id INTO wallet_id
        FROM _backfill_wallet_map wm WHERE wm.owner_id = rec.owner_id;

        IF wallet_id IS NULL THEN
            wallet_id := gen_random_uuid()::VARCHAR;
            INSERT INTO _backfill_wallet_map (owner_id, wallet_id) VALUES (rec.owner_id, wallet_id);
        END IF;

        UPDATE accounts
        SET coa_path = 'wallet:' || rec.owner_id || ':' || wallet_id || ':reserved',
            ledger   = 'SUB'
        WHERE id = rec.id;

        -- Insert GL mirror row
        INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version, created_at)
        SELECT gen_random_uuid()::VARCHAR, owner_id,
               'wallet:' || rec.owner_id || ':' || wallet_id || ':reserved',
               'GL', status, 0, NOW()
        FROM accounts WHERE id = rec.id;
    END LOOP;

    -- Create clearing rows for each wallet (new state, no existing rows to migrate)
    FOR rec IN
        SELECT DISTINCT owner_id,
               split_part(coa_path, ':', 3) AS wallet_id_part
        FROM accounts
        WHERE coa_path LIKE 'wallet:%:main'
          AND ledger = 'SUB'
    LOOP
        -- SUB clearing row
        INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version, created_at)
        VALUES (gen_random_uuid()::VARCHAR, rec.owner_id,
                'wallet:' || rec.owner_id || ':' || rec.wallet_id_part || ':clearing',
                'SUB', 'ACTIVE', 0, NOW());

        -- GL mirror clearing row
        INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version, created_at)
        VALUES (gen_random_uuid()::VARCHAR, rec.owner_id,
                'wallet:' || rec.owner_id || ':' || rec.wallet_id_part || ':clearing',
                'GL', 'ACTIVE', 0, NOW());
    END LOOP;

    -- Populate wallets table from backfill map
    INSERT INTO wallets (id, customer_id, is_primary, status, created_at)
    SELECT wallet_id, owner_id, TRUE, 'ACTIVE', NOW()
    FROM _backfill_wallet_map;

END $$;

-- Drop temporary backfill map
DROP TABLE IF EXISTS _backfill_wallet_map;

-- 11. Backfill: migrate system buffer account
--     Step 11 before Step 12 — update path first, then rename ID.
UPDATE accounts SET coa_path = 'bank:shared:main', ledger = 'GL'
WHERE id = 'acct_0000000000000000000BUFFER_USD';

-- 12. Rename buffer account ID and update FK references in same transaction
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_account_id_fkey;
ALTER TABLE balance_snapshots DROP CONSTRAINT balance_snapshots_account_id_fkey;
UPDATE accounts SET id = 'acct_0000000000000000000BANK_SHARED_MAIN'
    WHERE id = 'acct_0000000000000000000BUFFER_USD';
UPDATE ledger_entries SET account_id = 'acct_0000000000000000000BANK_SHARED_MAIN'
    WHERE account_id = 'acct_0000000000000000000BUFFER_USD';
UPDATE balance_snapshots SET account_id = 'acct_0000000000000000000BANK_SHARED_MAIN'
    WHERE account_id = 'acct_0000000000000000000BUFFER_USD';
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_entries_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE balance_snapshots ADD CONSTRAINT balance_snapshots_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts(id);

-- 14. Add new system GL accounts
--     NOTE: BANK_SHARED_MAIN is NOT inserted here — it already exists after Steps 10 and 11.
INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version) VALUES
('acct_00000000000000BANK_SHARED_CLEARING',     'user_00000000000000000000000000SYSTEM', 'bank:shared:clearing',           'GL', 'ACTIVE', 0),
('acct_000000000000000000BANK_PROP_MAIN',        'user_00000000000000000000000000SYSTEM', 'bank:prop:main',                 'GL', 'ACTIVE', 0),
('acct_00000000000000BANK_PROP_CLEARING',        'user_00000000000000000000000000SYSTEM', 'bank:prop:clearing',             'GL', 'ACTIVE', 0),
('acct_0000000000000EXTERNAL_STRIPE',            'user_00000000000000000000000000SYSTEM', 'external:counterparty:stripe',   'GL', 'ACTIVE', 0),
('acct_000000000000RECEIVABLE_STRIPE',           'user_00000000000000000000000000SYSTEM', 'receivable:counterparty:stripe', 'GL', 'ACTIVE', 0);

-- 13. Add new transaction types
INSERT INTO transaction_types (id, name, description) VALUES
('ttype_0000000000000DEPOSIT_RECEIVABLE', 'DEPOSIT_RECEIVABLE', 'Stage 1 — records receivable claim and customer reserved entry on deposit initiation'),
('ttype_00000000000000DEPOSIT_CONFIRMED', 'DEPOSIT_CONFIRMED',  'Stage 3 — two sub-transactions on webhook COMPLETED, linked by causation_id'),
('ttype_000000000000000000DEPOSIT_SWEEP', 'DEPOSIT_SWEEP',      'Stage 4 — sweep job moves wallet:clearing to wallet:main'),
('ttype_00000000000000000FEE_COLLECTION', 'FEE_COLLECTION',     'Fee collected from customer wallet, offset cleared to revenue');
```

---

### Phase 3 — `hcau-general-ledger` module

**Goal:** Remove duplicate CoA model, use `hcau-banking-common` classes, update account
provisioning to emit new-format paths, create wallets table rows, and create GL mirror rows at
provisioning time.

**Account provisioning — new flow:**
1. Generate `wallet_id` as UUID v7.
2. Insert row into `wallets` table (`id = wallet_id`, `customer_id`, `is_primary`, `status = ACTIVE`).
3. Create three `SUB` account rows: `wallet:{owner}:{wallet_id}:main`, `wallet:{owner}:{wallet_id}:reserved`, `wallet:{owner}:{wallet_id}:clearing`.
4. Create three `GL` mirror account rows with `ledger = GL` for the same paths.
5. Stripe metadata stays on the `main` account row via `account_providers` table. No Stripe data in the CoA path.

**Files to change:**
- `web/common/account/InternalCoa.java` → delete (use `CoaPath` from common)
- `web/common/account/InternalCoaFactory.java` → delete (use `CoaPathParser` from common)
- `web/common/account/BucketEnum.java` → delete (use `AccountState` from common)
- `web/service/AccountService.java` → rewrite account creation per flow above
- `web/model/account/AccountResponse.java` → replace `domain`, `bucketType` fields with
  `accountType`, `state`, `walletId` parsed from the new CoA path. Internal API — breaking change acceptable.

---

### Phase 4 — `hcau-banking-reconcile` module

**Goal:** Update deposit flow to use new CoA paths, new account states, and the resolved entry
structure defined above.

#### 4a. Account resolution

Replace `InternalCoaFactory.build(...)` lookups with `CoaPathParser` equivalents.

| Old lookup | New CoA path |
|---|---|
| `wallet:user:USD:RESERVED` (customer reserved) | `wallet:{customer_id}:{wallet_id}:reserved` |
| System AVAILABLE buffer | `bank:shared:main` |
| (new) receivable claim | `receivable:counterparty:stripe` |
| (new) bank clearing | `bank:shared:clearing` |
| (new) wallet clearing | `wallet:{customer_id}:{wallet_id}:clearing` |

Add `AccountRepository.findWalletByOwner(ownerId)` to resolve `wallet_id` before constructing paths.

#### 4b. Deposit initiation entries (DEPOSIT_RECEIVABLE)

| Account | Entry |
|---|---|
| `receivable:counterparty:stripe` | DEBIT |
| `wallet:{customer_id}:{wallet_id}:reserved` | CREDIT |

#### 4c. Webhook COMPLETED entries (DEPOSIT_CONFIRMED)

Two transactions linked by `causation_id`.

**Sub-tx 1 — bank side confirmed:**

| Account | Entry |
|---|---|
| `bank:shared:main` | DEBIT |
| `receivable:counterparty:stripe` | CREDIT |

**Sub-tx 2 — customer wallet moves forward (`causation_id` → Sub-tx 1):**

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT |
| `wallet:{customer_id}:{wallet_id}:clearing` | CREDIT |

#### 4d. Sweep job — wallet clearing → main (DEPOSIT_SWEEP)

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | DEBIT |
| `wallet:{customer_id}:{wallet_id}:main` | CREDIT |

#### 4e. Webhook FAILED / VOIDED / EXPIRED entries

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT |
| `receivable:counterparty:stripe` | CREDIT |

**Files to change in `hcau-banking-reconcile`:**
- `service/DepositService.java` — update initiation entries to new paths
- `service/DepositWebhookService.java` — update COMPLETED (two sub-tx with causation_id) and reversal entries
- `job/DepositSweepJob.java` — sweep from `wallet:clearing` → `wallet:main` (not `reserved` → `available`)
- `repository/AccountRepository.java` — add `findWalletByOwner`, `findByCoaPath`

---

### Phase 5 — Seed data and V4 cleanup

After V8 migration, update `V4__seed_data.sql` to remove inserts that are no longer relevant.

Remove from `V4__seed_data.sql`:
- `domain_types` inserts (table dropped in V8)
- `bucket_types` inserts (table dropped in V8)

Review:
- `job_config` — review `sweep_interval_seconds`; sweep behavior changes from `reserved → available` to `clearing → main`
- `transaction_types` — `INBOUND_DEPOSIT` and `INBOUND_SWEEP` remain; new types added in V8

---

## File Change Summary

| Module | File | Action |
|---|---|---|
| `hcau-banking-common` | `common/account/AccountType.java` | Create |
| `hcau-banking-common` | `common/account/AccountState.java` | Create |
| `hcau-banking-common` | `common/account/LedgerType.java` | Create |
| `hcau-banking-common` | `common/account/WalletId.java` | Create |
| `hcau-banking-common` | `common/account/CoaPath.java` | Create |
| `hcau-banking-common` | `common/account/CoaPathParser.java` | Create |
| `hcau-banking-common` | `common/account/Account.java` | Edit — add `ledger`, rename `internalCoa` → `coaPath`, remove `currency` |
| `hcau-banking-common` | `repository/AccountRepository.java` | Edit — update queries |
| `hcau-banking-common` | `common/ledger/LedgerEntry.java` | Edit — add `currency` field |
| `hcau-banking-common` | `common/ledger/BalanceSnapshot.java` | Edit — update PK to `(account_id, currency)` |
| `hcau-banking-common` | `common/account/BucketEnum.java` | Delete after migration |
| `hcau-general-ledger` | `db/migration/V8__coa_redesign.sql` | Create |
| `hcau-general-ledger` | `web/common/account/InternalCoa.java` | Delete |
| `hcau-general-ledger` | `web/common/account/InternalCoaFactory.java` | Delete |
| `hcau-general-ledger` | `web/common/account/BucketEnum.java` | Delete |
| `hcau-general-ledger` | `web/service/AccountService.java` | Rewrite |
| `hcau-general-ledger` | `web/model/account/AccountResponse.java` | Edit |
| `hcau-banking-reconcile` | `service/DepositService.java` | Edit |
| `hcau-banking-reconcile` | `service/DepositWebhookService.java` | Edit |
| `hcau-banking-reconcile` | `job/DepositSweepJob.java` | Edit |
| `hcau-banking-reconcile` | `repository/AccountRepository.java` | Edit |
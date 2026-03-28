# Chart of Accounts (CoA)

## Path Format

Every account in the system is identified by a **CoA path** — a colon-separated string that encodes the account type, owner, and state.

```
{account_type}:{...segments}:{state}
```

### Account Types

| Type | Purpose |
|------|---------|
| `bank` | Platform-owned pool accounts (shared fiat pool, proprietary funds) |
| `wallet` | Customer-owned accounts (spendable, earmarked, in-flight) |
| `receivable` | Claims owed to the platform by an external provider, not yet settled |
| `offset` | Fee bridging accounts |
| `revenue` | Earned fee revenue |
| `external` | Boundary accounts at the provider edge (non-deposit flows only) |

### Path Schemas

| Account Type | Path Pattern | Example |
|---|---|---|
| bank | `bank:{shared\|prop}:{main\|clearing}` | `bank:shared:main` |
| wallet | `wallet:{customer_id}:{wallet_id}:{main\|reserved\|clearing}` | `wallet:550e..40:7f3a..b2:main` |
| receivable | `receivable:counterparty:{provider}` | `receivable:counterparty:stripe` |
| offset | `offset:segregated:{customer_id}:paid_fee` | `offset:segregated:550e..40:paid_fee` |
| revenue | `revenue:segregated:{customer_id}:fee` | `revenue:segregated:550e..40:fee` |
| external | `external:counterparty:{provider}` | `external:counterparty:stripe` |

### System Accounts (Seeded)

These accounts are owned by `SYSTEM` and seeded at startup. Adding a new provider requires a migration.

| CoA Path | Ledger | Purpose |
|---|---|---|
| `bank:shared:main` | GL | Shared fiat pool — confirmed funds |
| `bank:shared:clearing` | GL | Shared fiat pool — in-transit |
| `bank:prop:main` | GL | Platform proprietary — confirmed |
| `bank:prop:clearing` | GL | Platform proprietary — in-transit |
| `receivable:counterparty:stripe` | GL | Claim on Stripe, not yet settled |
| `external:counterparty:stripe` | GL | Boundary account at Stripe edge |

## Wallet States

Each customer wallet has three sub-accounts sharing the same `wallet_id`:

| State | Meaning |
|-------|---------|
| `main` | Available and spendable. Replaces the old `AVAILABLE` bucket. |
| `reserved` | Earmarked — operation initiated but not yet fired. |
| `clearing` | In-flight — waiting on external confirmation before final settlement. |

## GL vs SUB Ledger

Every account row carries a `ledger` column: `GL` or `SUB`.

| Ledger | Purpose |
|--------|---------|
| `GL` | General Ledger — aggregate control total, platform-wide |
| `SUB` | Sub-ledger — per-customer detail, balances must roll up to GL |

- Customer wallet accounts exist as **both** `SUB` (detail) and `GL` (mirror). Both are created at provisioning time.
- Bank, receivable, offset, revenue, and external accounts are `GL` only.

## Wallet Provisioning

When a new customer wallet is created:

1. Generate `wallet_id` as UUID v7.
2. Insert a row into `wallets` table (`id = wallet_id`, `customer_id`, `is_primary`, `status = ACTIVE`).
3. Create three `SUB` account rows: `wallet:{owner}:{wallet_id}:main`, `:reserved`, `:clearing`.
4. Create three `GL` mirror account rows for the same paths.
5. Stripe metadata stays on the `main` account row — no Stripe data in the CoA path.

## Currency

- Currency is **not** part of the CoA path or the `accounts` table.
- Currency lives on `ledger_entries` (per entry) and `balance_snapshots` (PK: `account_id, currency`).
- This allows a single account to hold entries in multiple currencies if needed.

## Deposit Flow Accounts

For reference on which accounts participate in each deposit stage, see the [Tracing Strategy](tracing.md) for correlation rules and [plan.md](../../../plan.md) for the full deposit flow table.

| Stage | Accounts Used |
|-------|--------------|
| 1 — Initiation | `receivable:counterparty:stripe` (DR), `wallet:...:reserved` (CR) |
| 3a — Bank confirmed | `bank:shared:main` (DR), `receivable:counterparty:stripe` (CR) |
| 3b — Wallet forward | `wallet:...:reserved` (DR), `wallet:...:clearing` (CR) |
| 4 — Sweep | `wallet:...:clearing` (DR), `wallet:...:main` (CR) |
| Reversal | `wallet:...:reserved` (DR), `receivable:counterparty:stripe` (CR) |

## Key Rules

1. **`receivable` is for deposits.** It records the in-flight claim before settlement. `external` is NOT used in the deposit flow — it belongs to other boundary flows.
2. **`wallet:clearing` provides rollback safety.** The clearing state sits between webhook COMPLETED and sweep, giving auditability and a safe reversal point.
3. **All IDs use UUID v7.** `wallet_id` uses UUID v7 from the start. Other IDs will migrate in a future pass.
4. **CoaPathParser is the only gate.** All path construction and validation goes through `CoaPathParser` — no raw string concatenation allowed when building paths for the DB.

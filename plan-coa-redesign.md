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
wallet:control:main                           -- GL control: aggregate of all customer main balances
wallet:control:reserved                       -- GL control: aggregate of all customer reserved balances
wallet:control:clearing                       -- GL control: aggregate of all customer clearing balances
wallet:{customer_id}:{wallet_id}:main         -- SUB: customer spendable balance
wallet:{customer_id}:{wallet_id}:reserved     -- SUB: earmarked, operation not yet fired
wallet:{customer_id}:{wallet_id}:clearing     -- SUB: in-flight, waiting confirmation
receivable:counterparty:{provider}            -- asset: inbound claim, provider owes platform (deposit)
receivable:counterparty:{provider}:return     -- asset: withdrawal return float, provider owes platform returned funds (14xx)
payable:counterparty:{provider}               -- liability: outbound commitment, platform owes provider (withdrawal)
offset:segregated:{customer_id}:paid_fee      -- fee bridging offset
revenue:segregated:{customer_id}:fee          -- earned fee revenue
-- planned: contra_revenue:segregated:{customer_id}:chargeback  -- chargeback reversal (not implemented yet)
external:counterparty:{provider}              -- funds confirmed at provider, not yet in platform bank
-- planned: offset:fx                         -- FX suspense, absorbs currency mismatch during conversion (19xx)
-- planned: revenue:fx_spread                 -- FX spread revenue earned by platform (41xx)
-- planned: expense:counterparty:{provider}:return_fee  -- unrecovered portion of late withdrawal partial return (51xx)
```

### Wallet states

| State      | Meaning                                     |
|------------|---------------------------------------------|
| `main`     | Available, spendable. Replaces `AVAILABLE`. |
| `reserved` | Locked before operation fires. Keeps name.  |
| `clearing` | In-flight, waiting on confirmation. New.    |

### Ledger distinction

Each account row carries a `ledger` column: `GL` or `SUB`.

- `GL` — system-owned. The GL must balance independently on every transaction.
- `SUB` — customer-owned detail. Carries the per-customer breakdown. Does not need to balance independently.

**How GL balances independently:** Every transaction that touches a SUB wallet account also writes a matching entry to the corresponding `wallet:control:{state}` GL account. This keeps the GL self-contained.

**GL-only accounts** (no SUB counterpart): `bank:*`, `receivable:*`, `payable:*`, `external:*`, `wallet:control:*`

**SUB accounts** (detail only): `wallet:{customer_id}:{wallet_id}:*`

**Account custody rules — no overlap between these three states:**

| Account | Whose system holds the money | Example |
|---|---|---|
| `external:counterparty:{provider}` | Third party (e.g. Stripe) | Confirmed Stripe payment, not yet swept |
| `bank:shared:clearing` | Platform's bank, pending confirmation | Pending ACH credit on bank statement |
| `bank:shared:main` | Platform's bank, confirmed | Fully settled funds |

These states are sequential, never overlapping. If it's at Stripe → external. If it's in our bank but pending → bank:clearing. If it's confirmed in our bank → bank:main.

**Account custody rules — receivable vs payable:**

| Account | Direction | Meaning |
|---|---|---|
| `receivable:counterparty:{provider}` | Inbound | Provider owes platform (deposit in flight) |
| `payable:counterparty:{provider}` | Outbound | Platform owes provider (withdrawal committed) |

**SUB entry classes — two types, both valid:**

- **(a) Cross-ledger entry** — one SUB entry, paired with GL control entries. Occurs when money enters or exits the SUB world: Stage 1 (earmark created), failure reversal (earmark cancelled). The SUB entry does not self-balance; its integrity is guaranteed by the GL control account.
- **(b) Intra-wallet entry** — two SUB entries, self-balancing. Occurs when money moves between wallet states: Stage 3/tx2 (reserved → clearing), Stage 4 (clearing → main). Both sides are SUB, so the SUB transaction balances independently.

This rule must be known by anyone writing reconciliation jobs. The check is: for every cross-ledger SUB entry, there must be a matching GL control entry in the same transaction.

**Reconciliation invariant** — control account balance must equal sum of its SUB details:

```sql
SELECT balance FROM balance_snapshots WHERE account_id = 'acct_WALLET_CONTROL_MAIN';

SELECT SUM(bs.balance) FROM balance_snapshots bs
JOIN accounts a ON bs.account_id = a.id
WHERE a.coa_path LIKE 'wallet:%:main' AND a.ledger = 'SUB';
-- These two must always be equal
```

---

## Resolved Decisions

| # | Decision | Resolution |
|---|---|---|
| 1 | GL balance independence | GL balances independently on every transaction via `wallet:control:{state}` accounts. Every SUB wallet entry is paired with a matching GL control entry. Per-wallet GL mirror rows are not used — the control account aggregates all customers. |
| 2 | DEPOSIT_CONFIRMED transaction structure | Two transactions linked by `trace_id` (internal correlation). Each carries a `causation_id` referencing the external event (e.g., Stripe event ID) for reconciliation. |
| 3 | receivable and external accounts | Seed at startup — new providers require a migration |
| 4 | wallet_id convention | UUID v7 — independent ID generated at provisioning, not derived from any account row. All IDs will migrate to UUID v7 in a future pass; wallet_id follows the same convention from the start. |
| 5 | BucketEnum during migration | Kept temporarily as alias, deleted once both services migrated |
| 6 | external vs receivable in deposit flow | Both are used in the deposit flow at different stages. `receivable:counterparty:{provider}` is the unconfirmed claim window (Stage 1 → Stage 3). `external:counterparty:{provider}` is the confirmed-but-not-yet-settled window (Stage 3 → Stage N bank settlement). When Stripe COMPLETED fires, receivable is closed and external is opened. When Stripe sweeps funds to the platform's bank, external is closed and `bank:shared:main` is debited. |
| 7 | wallet:clearing for deposits | Keep — provides rollback safety and auditability between webhook COMPLETED and sweep |
| 8 | AccountResponse API change | Internal only — breaking change is acceptable, no deprecation strategy needed |
| 9 | currency column on accounts | Drop from `accounts`. Add to `ledger_entries` (per entry) and change `balance_snapshots` PK to `(account_id, currency)`. |
| 10 | Backfill atomicity | Write two-pass backfill as PL/pgSQL inline in V8 — keeps migration self-contained and atomic under Flyway. |
| 12 | SUB entry balance rule | SUB has two entry classes: (a) cross-ledger — single SUB entry paired with GL control entries, used when money enters/exits the SUB world; (b) intra-wallet — two SUB entries, self-balancing, used when money moves between wallet states. SUB is a detail ledger; its integrity comes from the GL control account, not internal balance. Reconciliation jobs must know this rule. |
| 13 | Payable account | `payable:counterparty:{provider}` added as the outbound counterpart to `receivable`. Represents a committed withdrawal instruction the platform has given to a provider but that has not yet settled. Keeps the withdrawal flow structurally parallel to the deposit flow (deposit = receivable asset inbound; withdrawal = payable liability outbound). |
| 14 | Contra-revenue / chargebacks | `contra_revenue:segregated:{customer_id}:chargeback` planned but not implemented. Will be added when the chargeback flow is built. Gross revenue and reversals stay in separate accounts for clean audit visibility. |
| 15 | External vs bank:clearing boundary | `external:counterparty:{provider}` = funds at a third party outside platform control. `bank:shared:clearing` = funds in platform's bank, pending confirmation. `bank:shared:main` = funds in platform's bank, confirmed. These three states are sequential and never overlap. Whose system holds the money determines which account. |
| 16 | Reconciliation — external verification | The `wallet:control = SUM(SUB)` check proves only internal consistency. A separate job must ingest provider settlement reports (Stripe payout reports, bank statements) and match against `external:counterparty:{provider}` and `bank:shared:main`. Any delta is a reconciliation break requiring investigation. |
| 17 | Reconciliation — zero-sum per trace_id | At terminal state (completed or fully reversed), all GL ledger entries for a given `trace_id` must net to zero. Non-zero at terminal state means a flow is incomplete or malformed. Enforced by a scheduled job that flags violating trace_ids. |
| 18 | Reconciliation — cross-ledger validation | Every transaction containing a single-sided SUB entry (class a) must have a matching `wallet:control:*` GL entry of the same amount in the same transaction. This is a synchronous validation at posting time — not an async job. Reject the transaction if the rule is violated. |
| 19 | Withdrawal flow staging | Withdrawal follows the same three-state lifecycle as deposit: `main → reserved → clearing → (exits SUB)`. Stage 1 = customer initiates (main → reserved). Stage 2 = provider API called (reserved → clearing). Stage 3 = provider confirms (clearing exits SUB world, payable opened). Stage 4 = bank settles (payable closed, bank:shared:main decreases). Gives an approval/compliance window between reserved and clearing. |
| 20 | Internal transfer GL entries | Instant same-vault transfers (main → main between two customers) require no GL entries — aggregate `wallet:control:main` does not change. Staged transfers (through reserved or clearing) require GL entries to keep `wallet:control:{state}` in sync with SUB detail. |
| 21 | Global locking order | Every DB transaction touching more than one account must acquire row-level locks (`SELECT FOR UPDATE`) in ascending lexicographic order of `account_id` before writing any entries. Enforced at the repository layer via a single shared locking utility. No flow may bypass it. Prevents deadlock across all concurrent transaction types. |
| 22 | Transaction type classification | Every transaction type is classified as GL-required or GL-exempt. GL-exempt types (e.g. `INTERNAL_TRANSFER_INSTANT`) produce no GL entries by design. The reconciliation job uses this table to distinguish missing GL entries from intentionally absent ones. Classification table is the single source of truth — new types must be classified before the reconciliation job accepts them. See Reconciliation Rules section. |
| 23 | Stale trace_id thresholds | Standard staleness threshold: 72 hours. `WITHDRAWAL_LATE_REVERSAL` trace_ids: 14 days (bank returns take 5–10 business days). Reconciliation job applies threshold per transaction type, not globally. |
| 24 | Cross-currency transfers blocked | Same-currency enforced at API validation layer until product decisions are made (rate source, rate commitment, spread model, settlement timing). Cross-currency requires `offset:fx` and `revenue:fx_spread` CoA paths. Blocked on product input. |
| 25 | Late withdrawal reversal variants | Two variants based on whether Stage 4 (bank settlement) has occurred. Variant A: payable still open — single transaction closes payable, re-credits wallet. Variant B: payable already settled — Phase 1 re-credits wallet and opens receivable (platform absorbs float); Phase 2 settles receivable when bank return arrives. Variant selected by querying payable balance under the original trace_id. |
| 26 | Partial return handling | If provider returns less than full withdrawal amount, the difference is posted to `expense:counterparty:{provider}:return_fee`. Deferred — assume full returns until needed. |
| 11 | Provider trust model | Platform trusts the provider to settle. `receivable:counterparty:{provider}` is recorded at Stage 1 as a committed claim — not provisional. The platform accepts the risk that the provider may fail to confirm. Customer wallet still follows `reserved → clearing → main` for internal auditability and rollback safety, independent of the trust decision. |

---

## Deposit Flow (Authoritative)

**Design posture:** The platform trusts the provider to settle. The receivable is recorded at Stage 1
as a committed claim — the platform accepts the risk of provider failure. The customer wallet
follows `reserved → clearing → main` regardless, to preserve internal auditability and clean
reversal paths if the provider does fail.

### Stage 1 — Customer initiates deposit

Stripe has not confirmed anything. The platform records a receivable claim against Stripe (trusting
it will be honoured) and earmarks the customer balance.

**GL entries (self-balancing):**

| Account                          | Entry  | Meaning                                           |
|----------------------------------|--------|---------------------------------------------------|
| `receivable:counterparty:stripe` | DEBIT  | Claim on Stripe recorded — money not yet received |
| `wallet:control:reserved`        | CREDIT | Aggregate customer reserved liability increases   |

**SUB entries (customer detail):**

| Account                                     | Entry  | Meaning                  |
|---------------------------------------------|--------|--------------------------|
| `wallet:{customer_id}:{wallet_id}:reserved` | CREDIT | Customer funds earmarked |

### Stage 2 — Stripe payment initiated webhook

No ledger entries. Transaction status updated only. Stripe has acknowledged the payment is in
progress on their side but nothing has settled yet.

### Stage 3 — Stripe webhook COMPLETED

Two transactions linked by `causation_id`. Both posted in response to the COMPLETED webhook.

**Sub-tx 1 — provider boundary confirmed (GL only, no SUB wallet involved):**

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `external:counterparty:stripe` | GL | DEBIT | Money confirmed sitting at Stripe |
| `receivable:counterparty:stripe` | GL | CREDIT | Unconfirmed claim closed |

**Sub-tx 2 — customer wallet moves forward (`causation_id` → Sub-tx 1):**

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:reserved` | GL | DEBIT | Aggregate reserved liability decreases |
| `wallet:control:clearing` | GL | CREDIT | Aggregate clearing liability increases |

SUB entries (customer detail):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | SUB | DEBIT | Release earmark |
| `wallet:{customer_id}:{wallet_id}:clearing` | SUB | CREDIT | Move to in-flight |

### Stage N — Bank settlement (Stripe sweeps to platform bank)

Triggered by Stripe's payout cycle, not by a customer action.

| Account | Entry | Meaning |
|---|---|---|
| `bank:shared:main` | DEBIT | Funds arrived in platform's real bank |
| `external:counterparty:stripe` | CREDIT | Float at Stripe cleared |

### Stage 4 — Sweep job

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:clearing` | GL | DEBIT | Aggregate clearing liability decreases |
| `wallet:control:main` | GL | CREDIT | Aggregate main liability increases |

SUB entries (customer detail):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | SUB | DEBIT | Release in-flight |
| `wallet:{customer_id}:{wallet_id}:main` | SUB | CREDIT | Customer balance spendable |

### Failure / Reversal (any stage before COMPLETED)

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:reserved` | GL | DEBIT | Aggregate reserved liability decreases |
| `receivable:counterparty:stripe` | GL | CREDIT | Cancel claim |

SUB entries (customer detail):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | SUB | DEBIT | Release earmark |

---

## Withdrawal Flow (Authoritative)

**Design posture:** Mirrors the deposit flow. Withdrawal uses `payable` (liability outbound) where
deposit uses `receivable` (asset inbound). Customer wallet follows `main → reserved → clearing →
(exits SUB)` — same three-state lifecycle as deposit, giving a compliance/approval window between
initiation and provider API call.

### Stage 1 — Customer initiates withdrawal

Platform acknowledges request. Funds locked, not yet sent to provider.

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:main` | GL | DEBIT | Aggregate main liability decreases |
| `wallet:control:reserved` | GL | CREDIT | Aggregate reserved liability increases |

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:main` | SUB | DEBIT | Funds locked |
| `wallet:{customer_id}:{wallet_id}:reserved` | SUB | CREDIT | Earmarked for withdrawal |

### Stage 2 — Provider API called

Platform fires the withdrawal instruction to the provider. Funds move to clearing.

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:reserved` | GL | DEBIT | Reserved aggregate released |
| `wallet:control:clearing` | GL | CREDIT | Clearing aggregate increases |

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | SUB | DEBIT | Earmark released |
| `wallet:{customer_id}:{wallet_id}:clearing` | SUB | CREDIT | In-flight to provider |

### Stage 3 — Provider confirms execution

Provider confirms withdrawal sent. Funds exit the SUB world. Payable opened.

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:clearing` | GL | DEBIT | Clearing aggregate released |
| `payable:counterparty:stripe` | GL | CREDIT | Platform owes provider for the payout |

SUB entries (cross-ledger, class a):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | SUB | DEBIT | Funds exit SUB world |

### Stage 4 — Bank settlement

Provider executes the payout. Funds leave platform's bank. Payable closed.

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `payable:counterparty:stripe` | GL | DEBIT | Platform obligation settled |
| `bank:shared:main` | GL | CREDIT | Cash leaves platform's bank |

No SUB entries — no customer wallet touched at this stage.

### Failure / Reversal (any stage before Stage 3)

Reverse whichever stage last moved the wallet. Example: failure after Stage 2 (clearing populated):

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:clearing` | GL | DEBIT | Clearing aggregate released |
| `wallet:control:main` | GL | CREDIT | Funds returned to main aggregate |

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | SUB | DEBIT | In-flight cancelled |
| `wallet:{customer_id}:{wallet_id}:main` | SUB | CREDIT | Funds returned to spendable |

### Late Reversal — Variant A (before bank settlement)

Trigger: provider webhook indicates payout failure. `payable` balance > 0 (Stage 4 has not yet occurred).

| Account | Ledger | Type | Meaning | Class |
|---|---|---|---|---|
| `payable:counterparty:{provider}` | GL | DEBIT | Close liability — provider won't execute | — |
| `wallet:control:main` | GL | CREDIT | Re-establish platform liability to customer | — |
| `wallet:{customer_id}:{wallet_id}:main` | SUB | CREDIT | Customer funds restored | (a) cross |

GL balanced. SUB cross-ledger class (a) — `wallet:control:main` GL credit is the matching entry.

### Late Reversal — Variant B (after bank settlement)

Trigger: provider webhook indicates payout failure. `payable` balance = 0 (Stage 4 already settled). Two phases because bank settlement and customer wallet must be handled independently.

**Phase 1 — Customer wallet restored immediately (platform absorbs float):**

| Account | Ledger | Type | Meaning | Class |
|---|---|---|---|---|
| `receivable:counterparty:{provider}:return` | GL | DEBIT | Provider owes platform the returned funds | — |
| `wallet:control:main` | GL | CREDIT | Re-establish platform liability to customer | — |
| `wallet:{customer_id}:{wallet_id}:main` | SUB | CREDIT | Customer funds restored | (a) cross |

Platform now carries float: customer made whole but bank return not yet received. Float is visible via `receivable:counterparty:{provider}:return` balance. Using `:return` suffix isolates this claim from deposit receivables in the same provider namespace.

**Phase 2 — Bank return arrives (days later):**

`causation_id` must carry the **original withdrawal's external provider reference** (e.g. `po_stripe_WDR1`) — not the bank return reference. This allows the reconciliation engine to link the bank return to the specific Phase 1 float entry.

| Account | Ledger | Type | Meaning |
|---|---|---|---|
| `bank:shared:main` | GL | DEBIT | Cash arrives back in platform bank |
| `receivable:counterparty:{provider}:return` | GL | CREDIT | Claim settled — provider returned funds |

No SUB entries. Customer already made whole in Phase 1.

### Variant Selection Logic

```
1. Look up original withdrawal trace_id
2. Query payable:counterparty:{provider} balance for entries under that trace_id
3. payable balance > 0 → Variant A
4. payable balance = 0 → Variant B
```

### Gated Transfer Reversal

If a gated internal transfer (Case B) fails compliance while funds are in `reserved` or `clearing`, reverse the current state back to `main`. Transaction type: `GATED_TRANSFER_REVERSAL`.

**If funds are in `reserved`:**

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:reserved` | GL | DEBIT | Reserved aggregate released |
| `wallet:control:main` | GL | CREDIT | Main aggregate restored |

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{sender_id}:{sender_wid}:reserved` | SUB | DEBIT | Earmark cancelled |
| `wallet:{sender_id}:{sender_wid}:main` | SUB | CREDIT | Funds returned to spendable |

**If funds are in `clearing`:** same pattern, substitute `clearing` for `reserved`. Both ledgers balance internally.

---

## Internal Transfer Flow (Authoritative)

**Two cases with different GL requirements.**

### Case A — Instant same-vault transfer (main → main)

`wallet:control:main` does not change — aggregate platform liability is unchanged.
No GL entries needed. SUB entries only.

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{sender_id}:{sender_wid}:main` | SUB | DEBIT | Sender balance decreases |
| `wallet:{receiver_id}:{receiver_wid}:main` | SUB | CREDIT | Receiver balance increases |

### Case B — Staged transfer (through reserved or clearing)

`wallet:control:{state}` changes as funds move between states. GL entries required to stay in sync.

Each state transition writes the same GL+SUB pattern as the deposit/withdrawal flows. Example: sender main → sender reserved:

GL entries (self-balancing):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:control:main` | GL | DEBIT | Main aggregate decreases |
| `wallet:control:reserved` | GL | CREDIT | Reserved aggregate increases |

SUB entries (intra-wallet, class b):

| Account | Ledger | Entry | Meaning |
|---|---|---|---|
| `wallet:{sender_id}:{sender_wid}:main` | SUB | DEBIT | Sender locked |
| `wallet:{sender_id}:{sender_wid}:reserved` | SUB | CREDIT | Earmarked |

Subsequent state transitions follow the same pattern until funds arrive at receiver's main.

---

## Engineering Constraints

### Global Locking Order

Every database transaction touching more than one account row must acquire row-level locks in **ascending lexicographic order of `account_id`** before writing any ledger entries.

**Implementation steps (enforced by `LedgerEntryWriter`):**
1. Receive list of `(account_id, type, amount, currency)` tuples.
2. Sort by `account_id` ascending (lexicographic, case-sensitive).
3. `SELECT ... FOR UPDATE NOWAIT` (or with explicit timeout, e.g. `SET lock_timeout = '5s'`) each account row in sorted order. If any lock acquisition times out, the entire transaction rolls back immediately. No partial lock states are allowed to leak.
4. Validate balances (sufficient funds for debits).
5. Write `ledger_entries` rows.
6. Update `balance_snapshots`.
7. Commit. On any failure → full rollback.

**Why this prevents deadlock:**

Without rule — concurrent counter-transfers A→B and B→A each lock one account then block on the other. Deadlock.

With rule (assuming `acct_A` < `acct_B` lexicographically):
- Tx1 (A→B): locks `acct_A`, then locks `acct_B`
- Tx2 (B→A): waits on `acct_A` until Tx1 commits, then proceeds

Lock acquisition order is identical regardless of which direction the transfer runs. No deadlock possible.

**Note on UUIDv7 account IDs:** If `account_id` uses UUIDv7 (e.g. `acct_01H...`), lexicographic order follows chronological creation order due to the timestamp prefix. This is acceptable — the requirement is determinism, not any specific ordering. Accounts created within the same millisecond batch may have unpredictable relative order. If this causes issues in practice, add a secondary sort key (e.g. `coa_path`).

**Constraints:**
- `LedgerEntryWriter` must execute within its own transaction boundary (`REQUIRES_NEW` propagation). This prevents a lock timeout in the ledger write from corrupting an outer transaction.
- This utility is the only permitted path for writing ledger entries. No service or flow bypasses it. Enforced at code review.

---

## Reconciliation Rules

### Transaction Type Classification

| Transaction Type | GL Required | SUB Required | Notes |
|---|---|---|---|
| `DEPOSIT_RECEIVABLE` | Yes | Yes (a) | Cross-ledger: single-sided SUB credit |
| `DEPOSIT_CONFIRMED` | Yes | Yes (b) for sub-tx 2 | Sub-tx 1 GL-only, sub-tx 2 GL+SUB |
| `DEPOSIT_SWEEP` | Yes | Yes (b) | Intra-wallet: clearing → main |
| `DEPOSIT_REVERSAL` | Yes | Yes (a) | Cross-ledger: single-sided SUB debit |
| `BANK_SETTLEMENT` | Yes | No | GL-only: external → bank:main |
| `WITHDRAWAL_INITIATE` | Yes | Yes (b) | Intra-wallet: main → reserved |
| `WITHDRAWAL_PROVIDER_SENT` | Yes | Yes (b) | Intra-wallet: reserved → clearing |
| `WITHDRAWAL_CONFIRMED` | Yes | Yes (a) | Cross-ledger: single-sided SUB debit (clearing exits) |
| `WITHDRAWAL_SETTLED` | Yes | No | GL-only: payable → bank:main |
| `WITHDRAWAL_LATE_REVERSAL` | Yes | Yes (a) | Cross-ledger: single-sided SUB credit (funds re-enter) |
| `WITHDRAWAL_RETURN_SETTLED` | Yes | No | GL-only: bank:main restored, receivable settled |
| `INTERNAL_TRANSFER_INSTANT` | **No** | Yes (b) | GL-exempt — aggregate wallet:control:main unchanged |
| `INTERNAL_TRANSFER_GATED` | Yes | Yes (b) | Staged: GL control entries required at each state transition |
| `GATED_TRANSFER_REVERSAL` | Yes | Yes (b) | Reversal of staged transfer from reserved or clearing back to main |
| `FEE_COLLECTION` | Yes | Yes | Revenue recognition |

This table is the single source of truth. New transaction types must be classified here before the reconciliation job accepts them.

### Three Checks Per Trace ID (at terminal state)

**Check 1 — GL Zero-Sum**

If the trace_id has any GL entries: `SUM(debits) - SUM(credits) = 0`.
Fail → alert: GL imbalance.

**Check 2 — GL-Exempt Validation**

If the trace_id has zero GL entries: every transaction type under this trace_id must be `GL Required = No`.
Fail → alert: missing GL entries.

**Check 3 — Cross-Ledger Integrity**

For every single-sided SUB entry (class a): the same transaction must contain a `wallet:control:{state}` GL entry of the same amount and same direction (directional parity).
Fail → alert: cross-ledger integrity violation.

This check is hardcoded as a **synchronous pre-write validation in `LedgerEntryWriter`**, not only in the async reconciliation job. The transaction is rejected before it reaches the database if directional parity fails.

**Check 4 — Currency Homogeneity**

For any transaction classified as `INTERNAL_TRANSFER_INSTANT`: all `ledger_entries` in the payload must share the same `currency` value. If they differ, `LedgerEntryWriter` rejects the payload.

This enforces the cross-currency blocker at the write layer in addition to the API layer (defense in depth). The write layer is the final gate.

### Stale Trace ID Monitor

Trace IDs in non-terminal states are skipped by the zero-sum check but monitored for age.

| Transaction type | Stale threshold |
|---|---|
| All types except late reversal | 72 hours |
| `WITHDRAWAL_LATE_REVERSAL` (Variant B) | 14 days (bank returns take 5–10 business days) |

---

## Planned Extensions (not implemented)

| Extension | Blocked on | CoA paths needed |
|---|---|---|
| Cross-currency transfers | Product decisions: rate source, commitment model, spread model | `offset:fx` (19xx), `revenue:fx_spread` (41xx) — same-currency enforced at two layers: API validation (rejects mismatched currency on request) and `LedgerEntryWriter` Check 4 (rejects `INTERNAL_TRANSFER_INSTANT` payload with mixed currencies). Defense in depth. |
| Chargeback / dispute flow | Feature scope definition | `contra_revenue:segregated:{customer_id}:chargeback` |
| Partial withdrawal return | Needed only when provider deducts fees from returns | `expense:counterparty:{provider}:return_fee` (51xx) |

These paths are documented in the CoA format section as comments. No accounts are created until the feature is implemented.

---

## Migration Strategy

### Phase 1 — CoA path model in `hcau-banking-common`

Move CoA logic from `hcau-general-ledger` into the shared module so both services use it.

**New classes:**

- `AccountType` enum: `BANK`, `WALLET`, `RECEIVABLE`, `PAYABLE`, `OFFSET`, `REVENUE`, `EXTERNAL` (planned: `EXPENSE`, `CONTRA_REVENUE`)
- `AccountState` enum: `MAIN`, `RESERVED`, `CLEARING`, `PAID_FEE`, `FEE`
- `LedgerType` enum: `GL`, `SUB`
- `WalletId` value type: wraps UUID v7 string. Used as `wallet_id` in CoA paths.
- `CoaPath` record: replaces `InternalCoa`. Holds parsed path segments.
- `CoaPathParser` utility: replaces `InternalCoaFactory`. Builds, parses, and validates CoA paths.
  Validation here is the only gate against invalid paths reaching the DB.
- `LedgerEntryWriter` utility: shared repository function that enforces the global locking order rule. Accepts a list of `(account_id, type, amount, currency)` tuples, sorts by `account_id` ascending, acquires `SELECT FOR UPDATE` locks, validates balances, writes `ledger_entries`, updates `balance_snapshots`. All transaction writers must call this — no flow may write ledger entries directly.

**BucketEnum migration:**
- `AVAILABLE` → `AccountState.MAIN`
- `RESERVED` → `AccountState.RESERVED`
- `BucketEnum` is kept temporarily as an alias during migration. Deleted once both services migrate.

**CoaPath format rules:**

| Account type | Path segments | Ledger |
|---|---|---|
| bank | `bank:{shared\|prop}:{main\|clearing}` | GL |
| wallet control | `wallet:control:{main\|reserved\|clearing}` | GL |
| wallet | `wallet:{customer_id}:{wallet_id}:{main\|reserved\|clearing}` | SUB |
| receivable | `receivable:counterparty:{provider}` | GL |
| receivable return | `receivable:counterparty:{provider}:return` | GL |
| payable | `payable:counterparty:{provider}` | GL |
| offset | `offset:segregated:{customer_id}:paid_fee` | GL |
| revenue | `revenue:segregated:{customer_id}:fee` | GL |
| external | `external:counterparty:{provider}` | GL |

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

-- 3. Drop old unique constraint, add new unique constraint on coa_path alone
ALTER TABLE accounts DROP CONSTRAINT accounts_internal_coa_key;
ALTER TABLE accounts ADD CONSTRAINT accounts_coa_path_key UNIQUE (coa_path);

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
    END LOOP;

    -- Create clearing rows for each wallet (new state, no existing rows to migrate)
    FOR rec IN
        SELECT DISTINCT owner_id,
               split_part(coa_path, ':', 3) AS wallet_id_part
        FROM accounts
        WHERE coa_path LIKE 'wallet:%:main'
          AND ledger = 'SUB'
    LOOP
        INSERT INTO accounts (id, owner_id, coa_path, ledger, status, version, created_at)
        VALUES (gen_random_uuid()::VARCHAR, rec.owner_id,
                'wallet:' || rec.owner_id || ':' || rec.wallet_id_part || ':clearing',
                'SUB', 'ACTIVE', 0, NOW());
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
('acct_000000000000RECEIVABLE_STRIPE',           'user_00000000000000000000000000SYSTEM', 'receivable:counterparty:stripe', 'GL', 'ACTIVE', 0),
('acct_0000000000000000PAYABLE_STRIPE',          'user_00000000000000000000000000SYSTEM', 'payable:counterparty:stripe',    'GL', 'ACTIVE', 0),
('acct_00000000000WALLET_CONTROL_MAIN',          'user_00000000000000000000000000SYSTEM', 'wallet:control:main',            'GL', 'ACTIVE', 0),
('acct_0000000000WALLET_CONTROL_RESERVED',       'user_00000000000000000000000000SYSTEM', 'wallet:control:reserved',        'GL', 'ACTIVE', 0),
('acct_0000000000WALLET_CONTROL_CLEARING',       'user_00000000000000000000000000SYSTEM', 'wallet:control:clearing',        'GL', 'ACTIVE', 0);

-- 13. Add new transaction types
INSERT INTO transaction_types (id, name, description) VALUES
('ttype_0000000000000DEPOSIT_RECEIVABLE', 'DEPOSIT_RECEIVABLE', 'Stage 1 — records receivable claim and customer reserved entry on deposit initiation'),
('ttype_00000000000000DEPOSIT_CONFIRMED', 'DEPOSIT_CONFIRMED',  'Stage 3 — two sub-transactions on webhook COMPLETED, linked by causation_id'),
('ttype_000000000000000000DEPOSIT_SWEEP', 'DEPOSIT_SWEEP',      'Stage 4 — sweep job moves wallet:clearing to wallet:main'),
('ttype_00000000000000000FEE_COLLECTION', 'FEE_COLLECTION',     'Fee collected from customer wallet, offset cleared to revenue'),
('ttype_000000000000000BANK_SETTLEMENT',    'BANK_SETTLEMENT',              'Stage N — provider payout cycle sweeps confirmed funds from external to bank:shared:main'),
('ttype_00000000000WITHDRAWAL_INITIATE',    'WITHDRAWAL_INITIATE',          'W-Stage 1 — customer initiates withdrawal, wallet:main locked to wallet:reserved'),
('ttype_000000WITHDRAWAL_PROVIDER_SENT',    'WITHDRAWAL_PROVIDER_SENT',     'W-Stage 2 — provider API called, wallet:reserved moves to wallet:clearing'),
('ttype_0000000000WITHDRAWAL_CONFIRMED',    'WITHDRAWAL_CONFIRMED',         'W-Stage 3 — provider confirms execution, wallet:clearing exits SUB, payable opened'),
('ttype_0000000000WITHDRAWAL_SETTLED',      'WITHDRAWAL_SETTLED',           'W-Stage 4 — bank settlement, payable closed, bank:shared:main decreases'),
('ttype_000WITHDRAWAL_LATE_REVERSAL',       'WITHDRAWAL_LATE_REVERSAL',     'Late reversal — funds re-enter SUB world after terminal state (Variant A or B Phase 1)'),
('ttype_0000WITHDRAWAL_RETURN_SETTLED',     'WITHDRAWAL_RETURN_SETTLED',    'Variant B Phase 2 — bank return arrives, receivable settled, bank:main restored'),
('ttype_00000INTERNAL_TRANSFER_INSTANT',    'INTERNAL_TRANSFER_INSTANT',    'Instant same-vault transfer, SUB only, no GL entries'),
('ttype_000000INTERNAL_TRANSFER_GATED',     'INTERNAL_TRANSFER_GATED',      'Staged transfer through reserved/clearing, GL+SUB entries required'),
('ttype_0000000000DEPOSIT_REVERSAL',        'DEPOSIT_REVERSAL',             'Deposit failure/reversal before COMPLETED — reverses Stage 1 entries'),
('ttype_000000GATED_TRANSFER_REVERSAL',     'GATED_TRANSFER_REVERSAL',      'Reversal of staged internal transfer from reserved or clearing back to main');
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
4. Stripe metadata stays on the `main` account row via `account_providers` table. No Stripe data in the CoA path.

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

GL:

| Account | Entry |
|---|---|
| `receivable:counterparty:stripe` | DEBIT |
| `wallet:control:reserved` | CREDIT |

SUB:

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | CREDIT |

#### 4c. Webhook COMPLETED entries (DEPOSIT_CONFIRMED)

Two transactions linked by `causation_id`.

**Sub-tx 1 — provider boundary confirmed (GL only):**

| Account | Entry |
|---|---|
| `external:counterparty:stripe` | DEBIT |
| `receivable:counterparty:stripe` | CREDIT |

**Sub-tx 2 — customer wallet moves forward (`causation_id` → Sub-tx 1):**

GL:

| Account | Entry |
|---|---|
| `wallet:control:reserved` | DEBIT |
| `wallet:control:clearing` | CREDIT |

SUB:

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT |
| `wallet:{customer_id}:{wallet_id}:clearing` | CREDIT |

#### 4d. Bank settlement — Stripe sweeps to platform bank (BANK_SETTLEMENT)

Triggered by Stripe's payout cycle. Not tied to a specific customer deposit.

| Account | Entry |
|---|---|
| `bank:shared:main` | DEBIT |
| `external:counterparty:stripe` | CREDIT |

#### 4e. Sweep job — wallet clearing → main (DEPOSIT_SWEEP)

GL:

| Account | Entry |
|---|---|
| `wallet:control:clearing` | DEBIT |
| `wallet:control:main` | CREDIT |

SUB:

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:clearing` | DEBIT |
| `wallet:{customer_id}:{wallet_id}:main` | CREDIT |

#### 4f. Webhook FAILED / VOIDED / EXPIRED entries

GL:

| Account | Entry |
|---|---|
| `wallet:control:reserved` | DEBIT |
| `receivable:counterparty:stripe` | CREDIT |

SUB:

| Account | Entry |
|---|---|
| `wallet:{customer_id}:{wallet_id}:reserved` | DEBIT |

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
| `hcau-banking-common` | `common/ledger/LedgerEntryWriter.java` | Create — shared locking utility, only permitted path for writing ledger entries |
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

---

## Open Items

| # | Item | Blocked on |
|---|---|---|
| 1 | Partial return handling | Needs `expense` account type in CoA. Deferred until provider deducts fees from returns. |
| 2 | Cross-currency product decisions | Rate source, commitment model, spread model. Blocked on product input. |
| 3 | Chargeback / dispute flow | Requires `contra_revenue` CoA path. Not yet spec'd. |
| 4 | `LedgerEntryWriter` implementation | Must be built as shared repository function before any flow goes to production. |
| 5 | `CoaPathParser` scope enforcement | `CoaPathParser` must reject any path where `external:*` or `bank:*` contains a customer ID segment. These are strictly GL-level concepts. Dynamic path generation that includes a customer scope on these account types must fail at parse time, not at the database. |
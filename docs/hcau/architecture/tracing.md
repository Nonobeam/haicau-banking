# Tracing Strategy

Every database-persisted operation that produces ledger entries or transactions **must** carry two identifiers: `trace_id` and `causation_id`. These serve different purposes and must not be conflated.

## Identifiers

### trace_id — Internal Correlation

- Groups all internal transactions that belong to the **same business flow**.
- Example: a deposit flow produces two transactions (Stage 1: webhook entry, Stage 2: sweep entry). Both share the same `trace_id`.
- Use `trace_id` to answer: **"Show me everything that happened in this flow."**

### causation_id — External Origin

- Points back to the **external event** that triggered the flow (e.g., a Stripe event ID).
- Each transaction in the flow carries the same `causation_id` referencing the originating external event.
- Use `causation_id` to answer: **"Which external event caused these entries?"** — essential for reconciliation against external providers.

## Rules

1. **Always populate both.** Any logic that persists a transaction or ledger entry must set `trace_id` and `causation_id`.
2. **`trace_id` is generated internally.** Create a new `trace_id` (UUID v7) at the start of each business flow.
3. **`causation_id` comes from outside.** Use the external system's event identifier (e.g., Stripe `event.id`). If there is no external trigger (purely internal operation), `causation_id` may equal `trace_id`.
4. **Never reuse `trace_id` across independent flows.** Each distinct business operation gets its own `trace_id`.
5. **Same `causation_id` across retries.** If the same external event is processed again (idempotency), the `causation_id` remains the same — this is how you detect and deduplicate.

## Example: Deposit Flow

| Stage | Transaction | trace_id | causation_id |
|-------|------------|----------|--------------|
| 1 — Webhook COMPLETED | DR `receivable:counterparty:stripe` / CR `wallet:clearing` | `flow-uuid` | `evt_stripe_xxx` |
| 2 — Sweep | DR `wallet:clearing` / CR `wallet:available` | `flow-uuid` | `evt_stripe_xxx` |

Both rows share `trace_id` (same flow) and `causation_id` (same Stripe event triggered both).

## Querying

- **Trace a full flow:** `SELECT * FROM ledger_entries WHERE trace_id = ?`
- **Reconcile with external provider:** `SELECT * FROM ledger_entries WHERE causation_id = ?`
- **Detect duplicate processing:** `SELECT count(*) FROM ledger_entries WHERE causation_id = ? AND stage = ?`

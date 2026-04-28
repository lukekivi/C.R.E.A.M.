# ADR 0001 — Plaid sync cursor is owned by the caller

- **Status:** Accepted
- **Date:** 2026-04-28
- **Affected components:** `functions/` (Firebase Cloud Functions), Android `:remote-datasource` and `:local-datasource` modules
- **Supersedes:** the initial design implied by the Plaid integration plan (cursor stored in Firestore alongside the access token).

## Context

Plaid's `/transactions/sync` endpoint returns deltas — `added`, `modified`, `removed` — relative to an opaque cursor string the caller hands back on each subsequent call. The cursor determines what the next call returns, so it must be durably persisted somewhere. Two candidate locations were available:

1. **Server.** Store the cursor in Firestore at `users/{uid}/items/{itemId}.cursor` alongside the Plaid access token. `syncTransactions` reads it, calls Plaid, writes the new value back.
2. **Caller.** The caller persists the cursor next to its local transaction cache, sends it up on each `syncTransactions` call, and stores the new value returned in the response.

The initial design (drafted in `.claude/plans/2026-04-27-001-plaid-bank-integration.md` and `.claude/conductors/2026-04-27-plaid-bank-integration/phase-02-functions-backend.md`) chose option 1. While implementing Phase 02 we identified a category of failure that option 1 cannot recover from cleanly:

> If the caller's local cache is wiped — app uninstall, sign-out, switching devices, factory reset, or any future cache-clearing migration — but the server's cursor still says "caught up," the next `syncTransactions` call returns an empty delta. The server has no information that would let it detect this divergence. The caller is left with an empty cache and no path back to the historical data short of disconnecting and re-linking the institution.

Recovering from this state under option 1 requires either:

- An additional `fullSync` flag on the wire and a per-caller heuristic to recognize "my cache should not be empty" (couples the contract to caller-side cache behavior); or
- A second persistence layer mirroring every transaction into Firestore, plus a `listTransactions` callable to backfill from it (doubles storage, adds a new endpoint, more state to keep consistent).

Both add complexity to repair a state inconsistency the alternative design eliminates by construction.

## Decision

**The cursor is owned by the caller.**

- `syncTransactions` accepts `{ itemId: string, cursor: string | null }` and returns `{ added, modified, removed, cursor }`.
- The server reads only the access token from Firestore, calls Plaid with the caller-supplied cursor, drains `has_more` server-side, and returns the new cursor along with the accumulated deltas. It persists nothing about sync state.
- A `null` cursor on input is the canonical "no prior progress, give me history" signal — equivalent in shape and behavior to a fresh install.
- The Firestore document at `users/{uid}/items/{itemId}` stores `{ accessToken, itemId, institutionId, institutionName, createdAt }` — no cursor field.

The caller is responsible for persisting the new cursor atomically with the deltas it just received. The conventional ordering is "write deltas first, then write cursor" so a crash mid-write replays a known set of deltas (idempotent on stable transaction IDs) rather than advancing past data that was never stored.

## Consequences

### Wins

- **Cache wipe is self-healing.** Cursor and cache wipe together; the next sync naturally requests history with `cursor: null`. No special path, no flag, no heuristic.
- **Cache and bookmark are atomically managed.** The caller can persist new transactions and the new cursor in a single local transaction, eliminating the "cursor advanced past data we never stored" failure mode.
- **The server is stateless with respect to sync.** Cloud Functions are simpler, easier to reason about, easier to test in isolation. The Firestore item document carries only what the server uniquely owns: the access token.
- **Wire format models the contract honestly.** The `cursor` field in the response is load-bearing — the caller is required to read it — rather than informational.

### Tradeoffs accepted

- **Multiple callers re-fetch independently.** If two callers (e.g. two devices, or a future web client) are linked to the same Firebase Auth user, each holds its own cursor and re-fetches its own history from Plaid on first sync. With option 1 plus server-side transaction storage, a second caller could backfill from Firestore. We accept redundant fetches; Plaid Production Trial has unlimited free calls and the cost is one-time per caller per Item.
- **No server-driven background sync.** A future Cloud Scheduler job that wants to call `syncTransactions` on the user's behalf (e.g. to keep webhook queues healthy) has no cursor of its own. Out of scope for V1; webhooks themselves are a follow-up.
- **No server-side rate limiting per Item.** Pacing is the caller's responsibility. Plaid imposes its own rate limits regardless, which the server propagates as `HttpsError("internal", ...)`.

### Reversibility

The decision is forward-compatible. Each of the following is purely additive and would not break the existing wire format:

- **Reducing Plaid API call volume by storing transactions in Firestore.** If we hit a Plaid plan ceiling, costs become meaningful, or we want callers to share data without re-fetching, the migration is: have `syncTransactions` write the deltas it returns into Firestore as a side effect, and add a `listTransactions(itemId)` callable that reads from Firestore. New callers backfill from Firestore on first use; subsequent syncs continue to drain Plaid via the caller-owned cursor. The existing `syncTransactions` wire format does not change. **This is the most likely future evolution and the design is set up to make it cheap.**
- **Adding a server-owned cursor as a secondary bookmark.** For a webhook handler or scheduled sync job that needs its own progress marker, store it under a different field on the same item doc (e.g. `serverCursor`). It doesn't conflict with caller-owned cursors — they're independent positions in the same Plaid stream.
- **Caching `listInstitutions` results in Firestore.** Same pattern: write the per-Item account snapshot at sync time, serve it from Firestore on subsequent reads, fall back to live Plaid only on explicit refresh.

In every case the migration path is "add new state and optionally a new endpoint," never "change an existing wire format."

## Triggers to revisit

Reopen this decision if any of the following becomes true:

- **Plaid API call volume becomes a constraint** — we approach a plan ceiling, the cost gets uncomfortable, or we add a second caller (web, iOS, integrations) and redundant fetches per caller become painful. The mitigation here is server-side transaction storage (see "Reducing Plaid API call volume" under Reversibility above) — keep the caller-owned cursor, add Firestore as a sharable cache. The existing wire format already supports this without breakage.
- Plaid webhooks are integrated and we want server-side state to coordinate "needs sync" flags across multiple callers.
- Scheduled or cron-driven sync becomes a requirement (no caller present at sync time).
- A bug surfaces that the caller-owned design cannot solve cleanly.
- Plaid changes its `/transactions/sync` semantics in a way that makes caller-owned cursors unsafe (e.g. cursor invalidation rules tightened beyond what callers can practically handle).

## Implementation references

- `functions/src/syncTransactions.ts` — the stateless proxy.
- `functions/src/firestore.ts` — `ItemDoc` (no `cursor` field).
- `functions/src/exchangePublicToken.ts` — writes the item doc without a cursor.
- `.claude/conductors/2026-04-27-plaid-bank-integration/phase-02-functions-backend.md` — phase plan reflecting this contract.
- `.claude/plans/2026-04-27-001-plaid-bank-integration.md` — underlying plan reflecting this contract (Phase 2 server side + Phase 4/5 caller side).

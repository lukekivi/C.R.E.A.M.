---
id: CREAM-014
title: Track transaction sync state per institution
status: todo
created: 2026-11-10
depends-on: [CREAM-004, CREAM-005]
---

Show when an institution's transactions are syncing or failed to sync, on both the Institutions and Account detail screens. Sync a newly linked institution's transactions in the background, so they appear without a pull to refresh.

## Scope

- `InstitutionRepository.observeTransactionSyncState(itemId): Flow<TransactionSyncState>`, with `TransactionSyncState` (`Idle` / `Syncing` / `Failed(cause)`) in `:core`.
- At most one sync per item: a `syncTransactions(itemId)` call made while one is in flight joins it instead of starting another.
- `linkInstitution` starts the new item's sync in the background, on an app-lifetime scope provided by `AppContainer`. A failed background sync never fails the link.
- `clearLocalCache` cancels in-flight syncs before deleting, then resets every item's sync state.
- Institutions screen: each header shows "Syncing transactions…" while its item syncs, and a quiet "Couldn't sync transactions" line after a failure.
- Account detail screen: a syncing row while the account's item syncs, and an inline "Couldn't load transactions. Pull to refresh." row after a background failure. A failure of the screen's own refresh is reported only by its snackbar.

## Notes

- Split out of CREAM-004 and CREAM-005, so each screen ships as its own reviewable PR and this lands on top of both.

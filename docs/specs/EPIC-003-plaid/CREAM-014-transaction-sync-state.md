---
id: CREAM-014
title: Track transaction sync state per institution
status: done
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

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

This work was planned and built as part of CREAM-004 and CREAM-005, then split out at review so each screen ships as its own PR. The decisions below are carried over from those specs.

**Acceptance criteria**

- `linkInstitution` succeeds once the token exchange and balance refresh succeed, then syncs the new item's transactions in the background on an app-lifetime scope; a failed background sync does not fail the link.
- `InstitutionRepository.observeTransactionSyncState(itemId: String): Flow<TransactionSyncState>` (sealed type in `:core`, `com.lucaskivi.cream.core.institution.model`) emits the item's current state on collection, then on every change. It is `Syncing` while a `syncTransactions(itemId)` run is in flight (background or caller-started); concurrent calls share that one run. When it ends, it becomes `Idle` on success or cancellation, or `Failed(cause)` on failure; a new sync resets it to `Syncing`; `clearLocalCache()` resets every item to `Idle`.
- Each institution header shows a "Syncing transactions" indicator while its state is `Syncing`, including after navigating away and back, and a small, unobtrusive "Couldn't sync transactions" line while it is `Failed`. A failed background sync is surfaced there only: the link itself never looks failed. A successful pull to refresh clears it.
- While this account's item's `TransactionSyncState` is `Syncing`, the transaction area shows a loading state; any transactions already cached render as they arrive.
- While it is `Failed`, a small inline error ("Couldn't load transactions. Pull to refresh.") shows near the transaction list, with cached data still on screen. Pull to refresh clears it. A sync failure from the screen's own `refresh()` is reported only by the Snackbar, never also inline.
- Repository, `InstitutionsViewModel`, and `AccountDetailViewModel` sync-state behavior is covered by unit tests.

**Clarifications**

- **Post-link sync (user, resolved; supersedes recon's "no"):** `linkInstitution` completes once the balance refresh is done, so accounts show immediately. The new item's `syncTransactions` then runs in the background, and the UI shows a per-institution "Syncing transactions" state until it finishes. A failed background sync never makes the link look failed. The exchange result (`LinkedInstitution`) already carries the `itemId`; the repository currently discards it and now uses it.
- **Sync-state contract (warroom, cross-ticket; CREAM-005 consumes it):** per-item sync state is owned by `InstitutionRepository` and exposed as `fun observeTransactionSyncState(itemId: String): Flow<TransactionSyncState>`. It is `Syncing` while any `syncTransactions(itemId)` call is in flight, whoever started it (post-link background sync, pull to refresh, CREAM-005's refresh), and `Idle`/`Failed` after. It emits the current state on collection, then on every change. See Plan → Structural considerations.
- User (plan wave): sync state exposed as typed per-item `TransactionSyncState`, not a Set of raw itemIds.
- **`TransactionSyncState` placement and payload (`/plan`'s call):** it lives in `:core` at `core/src/main/java/com/lucaskivi/cream/core/institution/model/TransactionSyncState.kt` (package `com.lucaskivi.cream.core.institution.model`), next to `FinancialAccount` and `LinkedInstitution`. It is a domain data type, so per STYLE → Packages it goes in a `model` subpackage, and `:app` sees `:core` through `:data`'s `api` dependency. `Failed` carries `cause: Throwable`, the same payload as `NetworkResult.Failure`, so the repository passes the sync's failure through unchanged. The UI shows fixed copy and doesn't render the cause.
- **Background-sync scope (warroom, resolved by `/plan`):** an app-lifetime `CoroutineScope(SupervisorJob() + Dispatchers.Default)` created in `AppContainer` and injected into `InstitutionRepositoryImpl`. It survives navigation, since `AppContainer` lives on the `Application`. Not WorkManager: process death mid-sync loses nothing, because the cursor is saved only after its delta is applied (ADR 0001), so the next sync resumes.
- User (work wave): concurrent syncs of the same item share the in-flight run instead of queuing. Replaces the per-item `Mutex` and the in-flight refcount: at most one sync runs per item, as a shared `Deferred` in `externalScope`; any `syncTransactions(itemId)` call made while it runs awaits it and gets its result. Cancelling a caller only stops its await. A caller still active when the shared run is cancelled (by `clearLocalCache`) gets `NetworkResult.Failure(CancellationException)`, so its coroutine isn't silently ended; a cancelled caller still gets the `CancellationException`, per `catching`'s convention.
- **No double-reporting of sync failures (005 `/plan`'s call).** The Snackbar owns failures of the screen's own `refresh()`; the inline error owns background (`Failed`) failures. The ViewModel keeps a private "sync failure acknowledged" flag: `refresh()` sets it on start (pull to refresh clears the inline error) and again if its own `syncTransactions` fails (the Snackbar already reported it); any non-`Failed` sync-state emission resets it, so a later background failure shows again. Inline error visible = state is `Failed` and not acknowledged.

**Deviations at `/work`**

- **Acknowledged-flag reset.** Because a refresh's sync may join a background run (the work-wave join decision above), its `Syncing`/`Failed` emissions can't be told apart from a background run's. Instead of re-acknowledging after its own sync fails, the ViewModel resets the flag on non-`Failed` emissions that arrive while no refresh is in progress, and when a refresh finishes with no error (so a conflated background `Syncing`→`Failed` right after it still shows). This also avoids a brief inline-error flash between the shared run failing and `refresh()` resuming. Tests cover the joined-run case and a later background failure showing again.

**Plan**

### Goal

Report each institution's transaction sync state on both screens, run at most one sync per item, and sync a newly linked institution in the background.

### Approaches considered (sync-state ownership and background scope)

1. **Repository-owned state + app-lifetime scope injected into the repository (chosen).** `syncTransactions` itself marks the item as syncing, so every caller reports through one place. `linkInstitution` launches the background sync on the injected scope. Pros: one source of truth across screens; nothing ViewModel-scoped; no new dependencies. Cons: the repository gains a scope and an in-memory state. Failure mode: an uncaught throw in a background job crashes the app. Mitigated by making `syncTransactions` never throw (see Refactoring).
2. **A separate `TransactionSyncCoordinator` in `:data`.** Cleaner single-purpose class, but it splits one data type's logic across two singletons, and CREAM-005 would then need two dependencies. Rejected as a premature split.
3. **WorkManager job.** Survives process death, but adds a dependency, a Worker, and a factory. Its progress must be bridged back into a Flow. ADR 0001's caller-owned cursor already makes a killed sync safe to retry. Rejected (YAGNI).

### Structural considerations

**Sync-state contract (cross-ticket; CREAM-005 relies on this exactly):**

```kotlin
// :core — core/src/main/java/com/lucaskivi/cream/core/institution/model/TransactionSyncState.kt
package com.lucaskivi.cream.core.institution.model

sealed interface TransactionSyncState {
    data object Idle : TransactionSyncState
    data object Syncing : TransactionSyncState
    data class Failed(val cause: Throwable) : TransactionSyncState
}

// :data — InstitutionRepository
fun observeTransactionSyncState(itemId: String): Flow<TransactionSyncState>
```

(Multiline KDoc on each declaration per STYLE, vendor-neutral since it's a `:core` model. `Idle` covers both "never synced this session" and "last sync succeeded".)

- State semantics: `Syncing` while the item's sync runs. When it ends, `Idle` if it succeeded or `Failed(cause)` if it returned `Failure`. Cancellation ends as `Idle`, since it isn't a failure. A new sync resets to `Syncing`. Unknown itemIds are `Idle`.
- One sync per item (user, work wave): `syncTransactions(itemId)` joins the run already in flight for that item, or starts one. Each run is a lazily started `async` in `externalScope`, registered in a lock-guarded `Map<String, Deferred<NetworkResult<Unit>>>` before it starts. Its completion handler removes the entry and sets the outcome, so the next call after that starts a fresh run. Overlapping read-cursor → apply → save-cursor sequences can't interleave, because only one runs.
- Callers `await()` the shared run without owning it, so cancelling a caller doesn't cancel the run. If the run itself is cancelled while the caller is active, the caller gets `Failure(CancellationException)`; if the caller is cancelled, the exception propagates.
- State lives in a repo-private `MutableStateFlow<Map<String, TransactionSyncState>>`; idle items are removed rather than stored. `observeTransactionSyncState(itemId)` = `map { it[itemId] ?: Idle }.distinctUntilChanged()`. Since it's derived from a `StateFlow`, a new collector gets the current state immediately.
- `clearLocalCache()` cancels and joins every in-flight run (background or caller-started), from a snapshot of the in-flight map, then clears the state map, so every item reads `Idle` (a run started after the snapshot stays `Syncing`).
- CREAM-005 usage: `observeTransactionSyncState(account.itemId)`.

**Background sync:**

- `InstitutionRepositoryImpl` takes a new constructor param `externalScope: CoroutineScope`. `AppContainer` passes `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, held as a private property. SupervisorJob means one failed job doesn't cancel siblings.
- `linkInstitution`: `exchangePublicToken` → `refreshInstitutions()` → on success, start (or join) the item's shared sync run without awaiting it → return `Success(Unit)`. If the exchange or the refresh fails, return Failure and launch nothing (the FK means transactions need the accounts the refresh writes). The return type stays `NetworkResult<Unit>`: the VM never needs the itemId, since sync state is observed. Update the KDoc to describe the post-link background sync contract without naming the vendor.
- `clearLocalCache()` cancels and joins in-flight syncs before deleting, so a sync finishing after sign-out can't write the previous user's transactions back. Track the launched jobs, e.g. children of a repo-private `SupervisorJob` parented to `externalScope`. `/work` picks the mechanics.

**UI:**

- Institutions: `screens/institutions/model/InstitutionItem(institution: LinkedInstitutionWithAccounts, syncState: TransactionSyncState)`. `InstitutionsViewModel` builds it with `flatMapLatest` over the institution list and `combine` over each item's `observeTransactionSyncState`, with an empty-list branch because `combine` of zero flows never emits. The header shows a status line chosen with `when (syncState)`: `Syncing` → a small `CircularProgressIndicator` + "Syncing transactions…"; `Failed` → "Couldn't sync transactions" in `bodySmall` / `colorScheme.error`; `Idle` → nothing.
- Account detail: the ViewModel follows `observeTransactionSyncState(account.itemId)` through `flatMapLatest`, combines it with the acknowledged flag, and exposes `isSyncingTransactions` and `isTransactionSyncFailed`. The screen shows a "Syncing transactions…" row and the inline "Couldn't load transactions. Pull to refresh." row; the empty state shows only when neither applies.

### Refactoring (before feature work)

- `syncTransactions`: wrap the local writes in `catching` (`flatMap { delta -> catching { … } }`) so a local write failure (e.g. FK violation after a concurrent disconnect) becomes `Failure` instead of throwing. This matches the interface KDoc, and an uncaught throw on the app scope would crash the app.
- `InstitutionRepositoryImplTest`: move `confirmVerified(remote, local)` into an `@AfterEach tearDown` per STYLE before adding tests. Existing per-test calls are removed.

### Gaps and follow-up

- A sync interrupted by process death isn't resumed automatically; the next pull to refresh catches up (ADR 0001). Revisit with WorkManager if periodic/background sync becomes a requirement.
- A `Failed` entry for a disconnected institution lingers in memory until the next `clearLocalCache`. Harmless, since nothing observes it.

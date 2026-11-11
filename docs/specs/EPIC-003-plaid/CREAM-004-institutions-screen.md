---
id: CREAM-004
title: Build the Institutions screen
status: done
created: 2026-10-19
depends-on: [CREAM-002, CREAM-003]
---

A screen listing every linked institution with its accounts and balances grouped underneath. The user can link a new institution, pull to refresh, and disconnect one.

## Scope

- `MainRoute.Institutions`, reachable from a button on `HomeScreen`.
- `InstitutionsViewModel` (companion factory per the `cream-viewmodel` pattern) backed by `InstitutionRepository`:
  - Collects `observeInstitutionsWithAccounts()`.
  - `linkInstitution()` — obtains a link token, hands it to the launcher via a one-shot event; on `InstitutionLinkResult.Success` calls `institutionRepository.linkInstitution(...)`.
  - `refresh()` — `refreshInstitutions()` then `syncTransactions(itemId)` per institution.
  - `disconnect(itemId)` — with a confirmation dialog.
  - UiState covers loading, refreshing, linking, and error.
- `InstitutionsScreen` — grouped list (institution header → account rows with name, mask, type, balance), FAB to link, `PullToRefreshBox`, overflow menu on the header for disconnect, empty state.
- Register the entry in `MainNavGraph`.

## Out of scope

- Account detail navigation target (CREAM-005) — account rows can be non-clickable or route to a stub until then.
- Transaction sync state (syncing and failed indicators) and syncing a newly linked institution in the background (CREAM-014).

## Notes

- `InstitutionRepository` has no `createLinkToken` today; the remote datasource does. Adding it to the repository is likely part of this ticket.
- Currency: all target institutions are USD. Confirm whether `isoCurrencyCode`-aware formatting is wanted now or later.

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- `MainRoute.Institutions` exists, is registered in `MainNavGraph`, and a button on `HomeScreen` navigates to it; back returns to Home.
- `InstitutionsViewModel` lives in `app/.../screens/institutions/`, is built through a companion `factory(appContainer)` per the `cream-viewmodel` pattern, and depends only on `InstitutionRepository` (no `com.plaid.*` imports).
- The screen lists every linked institution from `observeInstitutionsWithAccounts()`, each as a header followed by its account rows showing display name, mask, type, and balance; the list updates when the repository flow emits.
- With no linked institutions, the screen shows an empty state that offers linking.
- Tapping the link FAB obtains a link token from `InstitutionRepository` and opens Plaid Link through `rememberInstitutionLinkLauncher`. `InstitutionLinkResult.Success` calls `institutionRepository.linkInstitution(publicToken, institutionId, institutionName)`; `Cancelled` returns to idle with no error; `Error` and any failed link-token or link call surface an error message.
- Pull to refresh (`PullToRefreshBox`) calls `refreshInstitutions()` and then `syncTransactions(itemId)` for each linked institution, showing a refreshing indicator while it runs.
- The header's overflow menu offers Disconnect; confirming in a dialog calls `disconnectInstitution(itemId)`, dismissing the dialog does nothing.
- `UiState` represents loading, refreshing, linking, and error, and in-flight states clear when their operation finishes (success or failure).
- Account rows are non-clickable (account detail navigation is CREAM-005).
- `InstitutionRepository` exposes link-token creation, backed by `InstitutionRemoteDataSource.createLinkToken()`, with a repository test.
- `InstitutionsViewModel` behavior above is covered by unit tests in `app/src/test`.
- New declarations follow STYLE.md (multiline KDoc everywhere, ViewModel formatting, UiState below the ViewModel in the screen package).
- `./gradlew :app:assembleDebug` and the `:app` / `:data` unit tests pass.
- Account rows show `balanceCurrent` formatted by a reusable formatter using `isoCurrencyCode` (USD when null), or a placeholder when the balance is null.
- Pull to refresh stops with an error if `refreshInstitutions()` fails; otherwise it syncs every institution and surfaces one error if any sync failed.
- Errors appear as a snackbar; the empty state offers a "Link an institution" button.

**Reconnaissance findings**

- `data/src/main/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepository.kt` / `InstitutionRepositoryImpl.kt` — has observe/link/sync/disconnect/refresh/clearLocalCache, no link-token method. `linkInstitution` = `exchangePublicToken` then `refreshInstitutions()`; it does not sync transactions and does not return the new `itemId`. Last touched 17630e3, 0d78481, 4a494a0 (4a494a0's body lists the repository surface; the link-token omission was never discussed — no decision to honor).
- `remote-datasource/src/main/java/com/lucaskivi/cream/institution/InstitutionRemoteDataSource.kt:18` — `createLinkToken(): NetworkResult<String>` already exists (callable `createLinkToken`, verified end to end in CREAM-002).
- `app/src/main/java/com/lucaskivi/cream/link/InstitutionLinkLauncher.kt` + `link/model/InstitutionLinkResult.kt` — CREAM-003 output (7472f0e). `rememberInstitutionLinkLauncher(onResult)` returns a handle with `launch(linkToken)`; `Success(publicToken, institutionId, institutionName)` / `Cancelled` / `Error(message)`.
- `app/src/main/java/com/lucaskivi/cream/navigation/MainRoute.kt` — sealed class with Loading/Login/Register/Home; no Institutions route.
- `app/src/main/java/com/lucaskivi/cream/screens/main/MainNavGraph.kt` — Navigation 3 `NavDisplay` with a `mutableStateListOf<MainRoute>` back stack and `xxxEntry()` builders; `homeEntry()` currently takes no navigation callback.
- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeScreen.kt` / `HomeEntry.kt` / `HomeViewModel.kt` — closest existing pattern (screen + entry + VM + `app/src/test/.../screens/home/HomeViewModelTest.kt`). Home has only a Sign Out button today.
- `core/src/main/java/com/lucaskivi/cream/core/institution/model/` — `LinkedInstitutionWithAccounts(institution, accounts)`; `FinancialAccount` has `displayName`, `mask?`, `type: FinancialAccountType`, `subtype?`, `balanceCurrent?`, `balanceAvailable?`, `isoCurrencyCode?`.
- `app/build.gradle.kts` — material3 present; `:app` already has JUnit 5, MockK, coroutines-test for unit tests.
- STYLE.md sections that apply: Documentation, ViewModel formatting, Packages, Companion objects, Tests (Setup/Test/Verify, Confirm verified), Module / layer boundaries. `.claude/skills/cream-viewmodel` defines the factory pattern.
- Size: one repository method + test, one route, one entry, one screen, one ViewModel + tests, a Home button. Coherent single PR.

**Clarifications**

- The ticket gave Scope but no explicit done-conditions; the acceptance criteria above were derived from it (Partial at the start gate). `/plan` added the last three criteria to capture the decisions below.
- The ticket body already uses `InstitutionLinkResult` (CREAM-003's rename); no stale naming to reconcile.
- CREAM-005 stacks on this ticket: account rows stay non-clickable here; CREAM-005 adds the tap target and route. No `onAccountClick` seam is added now (an unused lambda is dead code; CREAM-005 adds it in a few lines).
- **Currency formatting (user, resolved):** format with the account's `isoCurrencyCode`, falling back to USD when it is null. Build a reusable formatter; CREAM-005 reuses it.
- **Balance (warroom, resolved):** show `balanceCurrent`, with a placeholder when it is null.
- **Refresh semantics (user, resolved):** `refresh()` = `refreshInstitutions()`, then `syncTransactions(itemId)` per institution. Abort if `refreshInstitutions()` fails. Otherwise sync every institution and surface a single error if any sync failed.
- **Error presentation and empty-state copy (`/plan`'s call):** errors are a `Snackbar` in the screen's `Scaffold`, cleared after it is shown. Copy is fixed per operation (see Plan); `InstitutionLinkResult.Error.message` is shown as-is because it is already user-facing. The empty state reads "No linked institutions yet" / "Link a bank or card to see your accounts and balances." with a "Link an institution" button.
- **Link token hand-off (`/plan`'s call):** a state field (`pendingLinkToken`) that the screen consumes and acknowledges, not a `Channel` event. This follows Android's state-over-events guidance; the repo has no event pattern yet.
- **Split (user, at review):** the post-link background sync, `observeTransactionSyncState`, one sync per item, and the header's sync indicator moved to CREAM-014, so this screen ships as its own PR. Until CREAM-014 lands, linking doesn't sync transactions; pull to refresh does.
- Observed drift outside this ticket (not acted on): CREAM-001 and CREAM-006 frontmatter still say `status: todo` though their commits (843e454; 0d78481, 24a168c) have landed.

**Plan**

### Goal

Ship the Institutions screen (list, link, pull to refresh, disconnect), plus the repository support it needs: link-token creation.

### Related code

- `data/.../repository/institution/InstitutionRepository.kt` / `InstitutionRepositoryImpl.kt` — gains `createLinkToken`.
- `data/src/test/.../InstitutionRepositoryImplTest.kt` — a test for it.
- `remote-datasource/.../institution/InstitutionRemoteDataSource.kt` — `createLinkToken()`.
- `app/.../navigation/MainRoute.kt`, `screens/main/MainNavGraph.kt` — new route and entry.
- `app/.../screens/home/HomeEntry.kt`, `HomeScreen.kt` — navigation callback and button.
- `app/.../link/InstitutionLinkLauncher.kt`, `link/model/InstitutionLinkResult.kt` — the launcher and the result type the VM consumes.
- `app/.../screens/home/HomeViewModel.kt` + `HomeViewModelTest.kt` — VM, factory, and test patterns to mirror.
- `app/.../screens/login/LoginViewModel.kt` — `error: String?` + `clearError()` pattern.

### Structural considerations

**Repository additions:** `suspend fun createLinkToken(): NetworkResult<String>`, delegating to the remote.

**UI (`:app`, new package `screens/institutions/`):**

- `InstitutionsViewModel(institutionRepository)`, with a companion `factory(appContainer)` per `cream-viewmodel`. UiState goes below the class.
- `InstitutionsUiState(isLoading = true, institutions: List<LinkedInstitutionWithAccounts> = emptyList(), isRefreshing, isLinking, pendingLinkToken: String?, pendingDisconnect: LinkedInstitution?, error: String?)`.
- `init`: collects `observeInstitutionsWithAccounts()` into `institutions`, clearing `isLoading`.
- `linkInstitution()`: `isLinking = true` → `createLinkToken()` → success sets `pendingLinkToken`; failure clears `isLinking` and sets the error "Couldn't start linking. Please try again." `onLinkLaunched()` clears `pendingLinkToken`. `onLinkResult(result)`: `Success` → `repository.linkInstitution(...)`, then clears `isLinking`, with the error "Couldn't link the institution. Please try again." on failure. `Cancelled` → clears `isLinking`. `Error` → clears `isLinking` and sets error = `result.message`. `isLinking` spans tap → result handled, which blocks double taps (the FAB is disabled while linking).
- `refresh()`: `isRefreshing = true` → `refreshInstitutions()`. Failure → error "Couldn't refresh your institutions." and stop. Success → itemIds from `observeInstitutionsWithAccounts().first()` (the post-refresh truth), then `syncTransactions` on all of them in parallel (`coroutineScope { map { async } .awaitAll() }`). If any sync failed → error "Couldn't sync transactions for some institutions." Then `isRefreshing = false`. One `return` per function (STYLE): branch, don't return early.
- `requestDisconnect(institution)` / `dismissDisconnect()` / `confirmDisconnect()`: confirm clears `pendingDisconnect` and calls `disconnectInstitution(itemId)`; failure → "Couldn't disconnect {name}. Please try again." `clearError()`.
- `InstitutionsScreen(onNavigateBack)`: `Scaffold` with a `TopAppBar` (title "Institutions", back arrow `Icons.AutoMirrored.Filled.ArrowBack`), a `SnackbarHost`, and an `ExtendedFloatingActionButton`/`FloatingActionButton` (`Icons.Default.Add`, hidden while linking). The content is `PullToRefreshBox(isRefreshing, onRefresh = viewModel::refresh)` wrapping either a centered progress indicator (loading), the empty state, or a `LazyColumn` of header + account rows.
  - Header: institution name and an overflow `IconButton` (`Icons.Default.MoreVert`) → `DropdownMenu` with "Disconnect".
  - Account row: `displayName`, then `•••• {mask}` when mask is non-null and a type label (enum → title case), plus the formatted `balanceCurrent` or the placeholder. Not clickable.
  - `AlertDialog` when `pendingDisconnect != null`: title "Disconnect {name}?", body "Its accounts and transactions will be removed from CREAM.", buttons "Disconnect" / "Cancel".
  - `rememberInstitutionLinkLauncher(viewModel::onLinkResult)`, plus `LaunchedEffect(pendingLinkToken)` that launches when non-null, then calls `onLinkLaunched()`. Branch with `if`, no early return.
  - `LaunchedEffect(error)` shows the snackbar, then calls `clearError()`.
  - Material3 1.3.0 (BOM 2024.09.00): `PullToRefreshBox` / `TopAppBar` need `@OptIn(ExperimentalMaterial3Api::class)`. Icons come via the transitive `material-icons-core`, so no new dependency. If they aren't on the classpath, add `material-icons-core` to the catalog.
- `InstitutionsEntry.kt`: `EntryProviderScope<MainRoute>.institutionsEntry(onNavigateBack)`.
- `MainRoute.Institutions` data object. `homeEntry(onNavigateToInstitutions)` → `HomeScreen(onNavigateToInstitutions)` adds an "Institutions" button above Sign Out. `MainNavGraph` wires `backStack.add(MainRoute.Institutions)` and `institutionsEntry(onNavigateBack = { backStack.removeLastOrNull() })`.

**Reusable currency formatter:** `app/src/main/java/com/lucaskivi/cream/ui/format/CurrencyFormatting.kt`.

- `fun formatCurrency(amount: Double, isoCurrencyCode: String?, locale: Locale = Locale.getDefault()): String` uses `NumberFormat.getCurrencyInstance(locale)` with `currency = Currency.getInstance(code)`. A null code or an unknown code (`IllegalArgumentException`) falls back to USD.
- `const val MISSING_AMOUNT_PLACEHOLDER = "—"` lives in the same file, so CREAM-005 reuses both.
- It's in `:app` `ui/`, not `:core`, because formatting is presentation. It is pure JVM, so it can be unit tested.

**PHAME:** Hierarchy holds (`:app` → `:data` only; the VM sees no `com.plaid.*`). Refresh-all orchestration stays in the VM per the ticket: it is screen-specific composition of two public repository calls.

### Implementation plan

- [x] Repository: `createLinkToken` + a test that it delegates.
- [x] `formatCurrency` + `MISSING_AMOUNT_PLACEHOLDER` + `CurrencyFormattingTest`: USD fallback on null, a non-USD code (e.g. EUR with a fixed `Locale.US`), an invalid code falling back, and negatives.
- [x] `MainRoute.Institutions`, `InstitutionsViewModel` + `InstitutionsUiState`, `InstitutionsScreen`, `InstitutionsEntry`, Home button/callback, and `MainNavGraph` wiring.
- [x] `InstitutionsViewModelTest` (JUnit 5 `@Nested` per method, Setup/Test/Verify, `confirmVerified` in tearDown, `UnconfinedTestDispatcher` on Main like `HomeViewModelTest`), covering:
  - init: lists every institution and follows list changes. An empty list still clears `isLoading`.
  - `linkInstitution`: token → `pendingLinkToken`; token failure → error.
  - `onLinkLaunched` clears the token.
  - `onLinkResult`: Success → link called; success/failure → `isLinking` cleared/error. Cancelled → idle, no error. Error → message shown.
  - `refresh`: refresh failure stops with no syncs; all syncs succeed → no error; one sync fails → single error, others still called; `isRefreshing` clears in every path.
  - Disconnect: request/dismiss/confirm (success and failure).
  - `clearError`.

### Impact assessment

- Code paths: the `InstitutionRepository` interface grows `createLinkToken`. Its only implementor and the test mocks need updating; `HomeViewModelTest`'s strict mock is unaffected.
- Behavior change: none outside the new screen.
- Data/schema: none. Dependencies: none expected (icons are transitive in material3 1.3.0).

### Validation

- `./gradlew :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
- STYLE pass on the diff: multiline KDoc on every non-test declaration, including `@param`s, private composables, and the formatter constant. Also check the single-return rule, method chains, and the expression-body newline rule.
- Manual (physical Pixel, emulator wiring per CREAM-002):
  1. Home → Institutions → back works.
  2. The empty state shows with no items.
  3. Link a sandbox institution. Accounts with balances appear.
  4. Pull to refresh shows the indicator.
  5. Disconnect → dialog: Cancel does nothing, confirm removes the item.
  6. Airplane mode + refresh → snackbar.

### Gaps and follow-up

- Disconnect has no in-flight indicator; the row disappears when the flow emits. Add one if the call proves slow.

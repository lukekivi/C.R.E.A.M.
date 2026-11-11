---
id: CREAM-005
title: Build the Account detail screen
status: done
created: 2026-10-19
depends-on: [CREAM-004]
---

Tapping an account on the Institutions screen opens a detail screen with the current balance and recent transactions.

## Scope

- `MainRoute.AccountDetail(accountId)`; navigate from account rows on the Institutions screen, back returns there.
- `AccountDetailViewModel` (companion factory) collecting `observeAccount(accountId)` and `observeTransactions(accountId)`. `refresh()` resolves the account's `itemId` and calls `syncTransactions(itemId)`.
- `AccountDetailScreen` — large current balance, account name + mask + type, transactions grouped by date, pull to refresh, loading/error/empty states.
- Register the entry in `MainNavGraph`.

## Out of scope

- Showing transaction sync progress or background sync failures (CREAM-014).

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- `MainRoute.AccountDetail` carries an `accountId` and is registered in `MainNavGraph`.
- Tapping an account row on the Institutions screen (CREAM-004) pushes `MainRoute.AccountDetail(accountId)`; system back pops to the Institutions screen with its state intact.
- `AccountDetailViewModel` is built through a companion `factory(appContainer, accountId)` (the `cream-viewmodel` pattern, plus the route argument). It combines `observeAccount(accountId)` and `observeTransactions(accountId)` into one UiState.
- The screen shows the account's current balance prominently ("—" when `balanceCurrent` is null; `balanceAvailable` is never shown), plus its display name, mask (when present), and type. Currency uses CREAM-004's formatter. The type label comes from a shared `internal` helper in `ui/format/`, also used by the Institutions screen.
- Every cached transaction is shown in one lazy list (no cap, no paging), grouped under date headers, newest date first. Each row shows the merchant name (falling back to the name), the signed amount (`-$x` for outflows / positive `amount`, `+$x` for inflows / negative `amount`, inflows visually highlighted), and a pending marker when pending.
- Pull to refresh calls `refresh()`. It calls `refreshInstitutions()` (updates balances) and, only if that succeeds, `syncTransactions(itemId)` with `itemId` read off the observed `FinancialAccount`, showing the refreshing indicator until the chain finishes.
- If `observeAccount` emits null while the screen is open, the screen pops back to Institutions.
- States: loading until the first account emission; empty state when the account has no transactions; an error message when refresh returns `NetworkResult.Failure`, with already cached data still on screen.
- ViewModel unit tests cover state mapping, a successful refresh, and a failed refresh. They follow STYLE.md's Tests section; `HomeViewModelTest` is the in-repo precedent.
- All new code follows STYLE.md (multiline KDoc on every declaration, the single-return rule, parameter formatting).

**Reconnaissance findings**

- `data/.../repository/institution/InstitutionRepository.kt`: `observeAccount`, `observeTransactions` (newest first, via `ORDER BY date DESC` in `InstitutionDao`), and `syncTransactions(itemId)` already exist. This ticket needs no data-layer changes.
- `core/.../institution/model/FinancialAccount.kt`: already has `itemId`, so `refresh()` can resolve it without a new repo method. Also has `balanceCurrent: Double?`, `balanceAvailable`, `mask: String?`, `type: FinancialAccountType`, `subtype`, and `isoCurrencyCode`.
- `core/.../institution/model/Transaction.kt` has `date: LocalDate` (the grouping key), `amount`, `merchantName?`, `name`, `pending`, and `isoCurrencyCode?`.
- `data/.../InstitutionRepositoryImpl.kt`: `syncTransactions` writes only transactions and the cursor. Only `refreshInstitutions()` updates account balances, so as the ticket is written, pull to refresh will not update the big balance (see Clarifications).
- `app/.../navigation/MainRoute.kt` is a sealed class of `data object`s. `AccountDetail` will be its first route with an argument.
- `app/.../screens/main/MainNavGraph.kt` uses Navigation 3 (`NavDisplay`, an `entryProvider` with `xxxEntry()` extensions per screen, and a `backStack` list). The new screen goes in `app/.../screens/accountdetail/`, per the EPIC-003 README.
- `app/src/test/.../screens/home/HomeViewModelTest.kt` is the precedent for ViewModel tests.
- ADR 0001 (`docs/decisions/0001-cursor-owned-by-caller.md`): the cursor stays owned by the repository/local cache. The screen just calls `syncTransactions(itemId)`.

**Clarifications**

- **Depends on CREAM-004, which does not exist in code yet.** This work stacks on CREAM-004's branch; at `/work` time this branch is rebuilt on it. Everything tagged **[004]** in the Plan comes from CREAM-004 and must be checked against its real code before building on it.
- **Integration seam:** CREAM-004 leaves account rows non-clickable and adds no click seam. This ticket adds an account-click callback that runs from the account row through `InstitutionsScreen` and `institutionsEntry` to `MainNavGraph`, which pushes `MainRoute.AccountDetail(accountId)`. It is the first route with an argument (Navigation 3).
- **Decisions (user, via warroom):**
  1. **Refresh** = `refreshInstitutions()` (updates balances), then `syncTransactions(itemId)` for this account's item. If `refreshInstitutions()` fails, stop: no sync, show the error.
  2. **Amount sign:** the provider convention is positive = money out. Outflows show as `-$x`, inflows as `+$x`, and inflows are visually highlighted.
  3. **Currency:** reuse CREAM-004's formatter (`isoCurrencyCode`, with a USD fallback). Don't build a second one.
- **Decisions (warroom):**
  6. **Missing account:** if `observeAccount` emits null while the screen is open, pop back to Institutions.
  7. **"Recent"** means every cached transaction, in one lazy list, grouped by date. No cap, no paging.
  8. **Null balance:** a null `balanceCurrent` shows "—". `balanceAvailable` is never shown.
  9. **Account-type label:** promote it to a shared `internal` helper in `ui/format/` and reuse it from both the Institutions and Account detail screens.
  10. **Inflow highlight:** keep `colorScheme.primary` + SemiBold. A semantic money palette belongs in a later theme ticket.
- **`/plan`'s calls (routine, made without the user):**
  - The screen gets a `TopAppBar` with a back arrow (same as 004's Institutions screen), titled with the account's display name.
  - The refresh error is a `Snackbar`, cleared after it is shown (004's pattern). Copy: "Couldn't refresh your balance." when `refreshInstitutions` fails; "Couldn't sync transactions." when `syncTransactions` fails.
  - ViewModel scoping: the ViewModel key includes `accountId` (see Structural considerations), so two accounts never share a ViewModel.
  - Date headers use `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`. No "Today"/"Yesterday" labels (YAGNI).
- **Split (user, at review):** the syncing row, the inline background-failure row, and the "acknowledged" flag that keeps the two from double-reporting moved to CREAM-014, so this screen ships as its own PR.

**Deviations at `/work`** (004 verified on the stacked branch; `formatCurrency`/`MISSING_AMOUNT_PLACEHOLDER`, `institutionsEntry`/`InstitutionsScreen`/`AccountRow`, and no ViewModel-store entry decorator all match the plan):

- **Shared formatting.** 004 had no standalone type-label helper (it was inlined in a private `FinancialAccount.details()`), and both screens need the same "•••• 1234 · Type" line and balance text. All three helpers moved to one `internal` file, `ui/format/FinancialAccountFormatting.kt` (`FinancialAccountType.toLabel()`, `FinancialAccount.formattedDetails()`, `FinancialAccount.formattedBalance()`), instead of `FinancialAccountTypeFormatting.kt`. The signed transaction amount and the inflow rule (`Transaction.formattedSignedAmount`, `Transaction.isInflow`) live beside them in `ui/format/TransactionFormatting.kt`, so the sign convention is stated once.
- **Manual on-device checks** were not run (no device attached); they are listed in the PR test plan.

**Plan**

### Goal

Tapping an account on the Institutions screen opens a detail screen with its balance and every cached transaction, grouped by date. Pull to refresh updates the balance and then the transactions. The screen closes itself if the account disappears.

### Approaches considered

1. **One ViewModel that combines the data flows into a `MutableStateFlow` UiState (chosen).** `init` collects `combine(observeAccount, observeTransactions)` into the data fields. `refresh()` updates `isRefreshing` and `error` on the same state. This matches `HomeViewModel` and 004's `InstitutionsViewModel`. Pros: one state source, easy to test with `MutableStateFlow` fakes. Cons: data fields and refresh fields share one `update`, so be careful not to clobber them (always `copy` only the fields you own).
2. **A `stateIn` pipeline that combines the data flows and a separate refresh-state flow.** More "reactive", but the repo has no precedent for it, and it adds a second pattern. Rejected.
3. **Missing-account pop as a one-shot `Channel` event.** Rejected: 004 chose state over events (`pendingLinkToken`). This ticket exposes `isAccountMissing` in state and the screen reacts with a `LaunchedEffect`. Popping is idempotent (`backStack.remove(route)`), so re-running the effect is harmless.

### Related code

- `app/.../navigation/MainRoute.kt` — add `data class AccountDetail(val accountId: String)`.
- `app/.../screens/main/MainNavGraph.kt` — register `accountDetailEntry` and wire `onAccountClick` into `institutionsEntry` **[004 adds the Institutions wiring]**.
- `app/.../screens/institutions/InstitutionsEntry.kt`, `InstitutionsScreen.kt` **[004]** — thread `onAccountClick: (String) -> Unit` down to the account row and make the row clickable.
- `app/.../ui/format/CurrencyFormatting.kt` **[004]** — `formatCurrency(amount, isoCurrencyCode, locale = default)` and `MISSING_AMOUNT_PLACEHOLDER`. Reused, not duplicated.
- `app/.../ui/format/FinancialAccountTypeFormatting.kt` (new) — the account-type label, promoted from 004's private helper in `InstitutionsScreen` **[004: current name/shape]**.
- `data/.../repository/institution/InstitutionRepository.kt` — `observeAccount`, `observeTransactions` (newest first), `refreshInstitutions`, `syncTransactions`.
- `core/.../institution/model/FinancialAccount.kt`, `Transaction.kt` — fields the screen renders. `Transaction.amount` KDoc confirms positive = debit.
- `app/.../screens/home/HomeViewModel.kt`, `HomeEntry.kt` — ViewModel, factory, and entry patterns.
- `app/.../screens/login/LoginViewModel.kt` — the `error: String?` + `clearError()` pattern.
- `app/src/test/.../screens/home/HomeViewModelTest.kt` — test precedent (setMain, `confirmVerified` in tearDown, `@Nested` per method).
- `.claude/skills/cream-viewmodel/SKILL.md` — the factory pattern.

### Structural considerations

**Placement:** everything new goes in `:app`, in package `com.lucaskivi.cream.screens.accountdetail` (per the EPIC-003 README). No changes to `:data`, `:core`, or the datasources. The one shared-UI change is promoting the account-type label to `ui/format/`.

**Route (`MainRoute.kt`):**

```kotlin
/**
 * Detail destination for a single financial account.
 *
 * @property accountId The account to show.
 */
data class AccountDetail(
    val accountId: String,
) : MainRoute()
```

Not `@Serializable`: the back stack is a plain `mutableStateListOf`, not saved across process death (unchanged from today).

**ViewModel (`AccountDetailViewModel.kt`):**

- Constructor: `(accountId: String, institutionRepository: InstitutionRepository)`, per-line with trailing commas.
- Companion `factory(appContainer: AppContainer, accountId: String)`. This extends the `cream-viewmodel` pattern with the route argument; the container stays the source of every dependency.
- `init`: `combine(observeAccount(accountId), observeTransactions(accountId))`. Each emission updates `isLoading = false`, `account`, `isAccountMissing = account == null`, and `transactionGroups = transactions.groupByDate()`.
- `refresh()`: read `itemId` from `uiState.value.account`. If null, do nothing (branch, no early return). Otherwise set `isRefreshing = true`, then `refreshInstitutions()`; on failure set error "Couldn't refresh your balance." and skip the sync; on success `syncTransactions(itemId)`, and on failure set error "Couldn't sync transactions.". Finally `isRefreshing = false`. Make it a single expression chain or `when`, one `return` at most (STYLE).
- `clearError()`.
- Grouping: `private fun List<Transaction>.groupByDate(): List<TransactionDateGroup>` uses `groupBy { it.date }`, which preserves the newest-first input order, so headers come out newest first without re-sorting. Use `this` in the extension (STYLE).

**UiState (below the ViewModel, same file):**

```kotlin
data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: FinancialAccount? = null,
    val isAccountMissing: Boolean = false,
    val transactionGroups: List<TransactionDateGroup> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)
```

**Model:** `screens/accountdetail/model/TransactionDateGroup.kt`: `data class TransactionDateGroup(val date: LocalDate, val transactions: List<Transaction>)` (a model type, so it goes in `model/` per STYLE).

**Screen (`AccountDetailScreen.kt`):** `AccountDetailScreen(accountId: String, onNavigateBack: () -> Unit)`.

- `viewModel(key = "AccountDetail:$accountId", factory = AccountDetailViewModel.factory(appContainer, accountId))`. `MainNavGraph` has no ViewModel-store entry decorator, so ViewModels are scoped to the Activity. Without the key, a second account would get the first account's ViewModel. **Verify against 004:** if 004 (or the rebase) adds `rememberViewModelStoreNavEntryDecorator`, the key is still harmless; keep it.
- `Scaffold`: a `TopAppBar` (title = `displayName`, back arrow → `onNavigateBack`) and a `SnackbarHost`. `LaunchedEffect(uiState.error)` shows the snackbar, then calls `clearError()`.
- `LaunchedEffect(uiState.isAccountMissing)`: if true → `onNavigateBack()`.
- Content: `PullToRefreshBox(isRefreshing, onRefresh = viewModel::refresh)` around:
  - loading → a centered `CircularProgressIndicator`;
  - otherwise a `LazyColumn`:
    - balance header: a large `formatCurrency(balanceCurrent, isoCurrencyCode)` or `MISSING_AMOUNT_PLACEHOLDER`, then `displayName`, `•••• {mask}` when present, and the type label from the shared `ui/format/` helper;
    - if there are no groups: the empty state "No transactions yet";
    - for each group: a `stickyHeader`/`item` date header, then a transaction row per transaction (key = `transactionId`).
- Transaction row: `merchantName ?: name`, a "Pending" label when `pending`, and the signed amount.
- Amount text: a private extension in the screen file. The sign comes from the convention (`amount > 0` → `-`, otherwise `+`; zero shows `+`). Then `formatCurrency(abs(amount), isoCurrencyCode ?: account.isoCurrencyCode)`. Format the absolute value, because `NumberFormat` already prefixes negatives. Inflows (`amount < 0`) get `colorScheme.primary` + SemiBold.
- `@OptIn(ExperimentalMaterial3Api::class)` for `PullToRefreshBox`/`TopAppBar` (Material3 1.3.0 via BOM 2024.09.00), same as 004.

**Entry (`AccountDetailEntry.kt`):**

```kotlin
fun EntryProviderScope<MainRoute>.accountDetailEntry(
    onNavigateBack: (MainRoute.AccountDetail) -> Unit,
) {
    entry<MainRoute.AccountDetail> { route ->
        AccountDetailScreen(
            accountId = route.accountId,
            onNavigateBack = { onNavigateBack(route) },
        )
    }
}
```

`MainNavGraph` passes `onNavigateBack = { route -> backStack.remove(route) }`. This removes *that* entry, not whatever is on top, so a late missing-account pop can't take another screen off the stack.

**Seam into 004 [004]:** `institutionsEntry(onNavigateBack, onAccountClick: (String) -> Unit)` → `InstitutionsScreen(onNavigateBack, onAccountClick)` → the account row gets `Modifier.clickable { onAccountClick(account.accountId) }`. `MainNavGraph` passes `onAccountClick = { accountId -> backStack.add(MainRoute.AccountDetail(accountId)) }`. Update the KDoc on every touched declaration (`@param`).

**Account-type label [004]:** move 004's private `FinancialAccountType` → label helper out of `InstitutionsScreen` into `app/.../ui/format/FinancialAccountTypeFormatting.kt` as an `internal` extension (e.g. `internal fun FinancialAccountType.toLabel(): String`, keeping 004's name if it already reads well). `InstitutionsScreen` and `AccountDetailScreen` both call it. Same treatment as `CurrencyFormatting.kt`.

**PHAME:** Hierarchy holds (`:app` → `:data`; no vendor types). Abstraction: formatting stays in the UI (locale-dependent); grouping is in the ViewModel (pure, testable). Modularization: a new feature package; currency and account-type formatting are shared via `ui/format`, not copied. Encapsulation: the screen only learns about the account through `accountId`; the account's item stays in the ViewModel.

### Implementation plan

- [x] **Verify 004 on the rebased branch first:** `formatCurrency` / `MISSING_AMOUNT_PLACEHOLDER` path and signature; `institutionsEntry` / `InstitutionsScreen` / account-row shapes; whether a ViewModel-store entry decorator was added; the name and shape of 004's `FinancialAccountType` label helper. Adjust the names below to match.
- [x] Promote the account-type label to `ui/format/FinancialAccountTypeFormatting.kt` (`internal`); switch `InstitutionsScreen` to it.
- [x] Add `MainRoute.AccountDetail(accountId)`.
- [x] Add `screens/accountdetail/model/TransactionDateGroup.kt`.
- [x] Add `AccountDetailViewModel` + `AccountDetailUiState` (combine, grouping, `refresh`, `clearError`, factory).
- [x] Add `AccountDetailScreen` (balance header, grouped `LazyColumn`, empty and loading states, pull to refresh, snackbar, missing-account pop, signed and highlighted amounts).
- [x] Add `AccountDetailEntry.kt`; register it in `MainNavGraph`.
- [x] Seam: thread `onAccountClick` through `institutionsEntry` → `InstitutionsScreen` → the account row; wire the push in `MainNavGraph`.
- [x] `AccountDetailViewModelTest`.
- [x] Build and run tests. (On-device check pending: no device attached.)

### Impact assessment

- Code paths affected: `:app` only. A new feature package; `MainRoute`; `MainNavGraph`; 004's Institutions entry, screen, and account row (an additive callback, plus the type-label helper moving to `ui/format/`).
- Data or schema impact: none.
- Dependency or API impact: none new. Uses 004's formatter.

### Validation

- **Tests** (`app/src/test/.../screens/accountdetail/AccountDetailViewModelTest.kt`): JUnit 5, `@Nested` per method, Setup/Test/Verify, `confirmVerified(institutionRepository)` in tearDown, and every test verifies the `observeAccount` and `observeTransactions` calls. Back them with `MutableStateFlow`s.
  - `Init`: maps the account and groups transactions by date, newest first; stays loading until the first emission; a null account sets `isAccountMissing`; transactions show as they arrive.
  - `Refresh`: success calls `refreshInstitutions` then `syncTransactions(itemId)` in order and ends with `isRefreshing = false`, no error; `refreshInstitutions` failure → no sync (`coVerify(exactly = 0)`), error set, cached data kept; `syncTransactions` failure → Snackbar error set, data kept; no account → no repository calls.
  - `ClearError`: clears the error.
- **Lint/format/typecheck:** `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Run the style reviewer on the diff (KDoc on every declaration, single return, parameter formatting, `this` in extensions).
- **Manual (physical Pixel via adb reverse, Plaid Sandbox):** tap an account → detail opens; back → Institutions with its state intact; pull to refresh updates the balance and transactions; outflows show `-$`, inflows show `+$` highlighted; pending marker; disconnect the institution from another path (or clear the cache) while the screen is open → it pops back; refresh in airplane mode → snackbar, data stays.

### Gaps and follow-up

- Process-death restore of the back stack (with route arguments) isn't handled. That is unchanged from today; the stack isn't saved.
- No transaction search, filtering, or paging. Revisit if cached history grows large.
- Semantic money palette (a dedicated income color) is deferred to a later theme ticket.

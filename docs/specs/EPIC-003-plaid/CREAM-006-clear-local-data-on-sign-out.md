---
id: CREAM-006
title: Clear cached institution data on sign-out
status: todo
created: 2026-10-19
---

`AppContainer` is a process-wide singleton and the Room database is not scoped to a user. If one user signs out and another signs in, the second user sees the first user's institutions, accounts, and transactions. Fix before any real data is linked.

## Scope

- Add a way to wipe the local institution cache (DAO + `InstitutionLocalDataSource` + `InstitutionRepository`).
- Invoke it on the sign-out path.
- Test: sign out → local cache empty.

## Notes

- `AuthRepository.signOut()` is currently non-suspending; clearing Room is a suspend operation. Decide where the orchestration lives (a ViewModel calling both repositories, a suspending sign-out, or similar) without making one repository depend on the other.
- Wiping the cache also discards stored sync cursors. Per ADR 0001 that is safe — a null cursor triggers a full resync.
- Alternative considered in the original plan: partition the DB by uid instead of clearing. Clearing is simpler; confirm.

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- A clear operation exists at each layer — `InstitutionDao`, `InstitutionLocalDataSource`, `InstitutionRepository` — that removes every row from `linked_institution`, `financial_account`, and `transaction` (stored sync cursors go with them).
- The user-initiated sign-out path (Home screen "Sign out") clears the local institution cache, then signs out.
- If the clear fails, sign-out still proceeds.
- `InstitutionRepository` and `AuthRepository` do not depend on each other.
- After sign-out, `observeInstitutionsWithAccounts()` emits an empty list; a subsequent `syncTransactions` for a re-linked item sends a `null` cursor (ADR 0001).
- Tests: the new clear path is covered at the datasource and repository layers, and the sign-out orchestration is covered by a test asserting the cache clear is invoked on sign-out.
- New declarations carry KDoc per STYLE.md.

**Reconnaissance findings**

- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeViewModel.kt` — only sign-out call site: `fun signOut() = authRepository.signOut()`, non-suspending. Factory currently injects only `authRepository`.
- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeScreen.kt` — button wired to `viewModel::signOut`.
- `data/src/main/java/com/lucaskivi/cream/data/repository/auth/AuthRepository.kt` / `AuthRepositoryImpl.kt` — `fun signOut()` (non-suspend) → `AuthDataSource.signOut()` → `firebaseAuth.signOut()` in `remote-datasource/.../auth/AuthDataSourceImpl.kt`. Its KDoc says "clears the local session" — Firebase session only today.
- `app/src/main/java/com/lucaskivi/cream/screens/main/MainViewModel.kt` — collects `authStateFlow` (drives signed-in/out routing); sign-out is observed here, not initiated.
- `data/src/main/java/com/lucaskivi/cream/data/di/AppContainer.kt` — process-wide singleton building both repositories independently; no cross-repo coordinator exists.
- `data/src/main/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepository.kt` / `InstitutionRepositoryImpl.kt` — no clear/wipe operation; `disconnectInstitution` deletes one item locally after remote success.
- `local-datasource/src/main/java/com/lucaskivi/cream/institution/InstitutionDao.kt` — has per-item `deleteInstitution(itemId)`; no delete-all. `InstitutionLocalDataSource.kt` / `InstitutionLocalDataSourceImpl.kt` mirror it.
- `local-datasource/.../FinancialAccountEntity.kt`, `TransactionEntity.kt` — FKs with `onDelete = CASCADE` up to `linked_institution`.
- `local-datasource/.../CreamDatabase.kt` — `version = 1`, three entities, singleton via `getInstance`; no migrations, no destructive fallback, no uid column anywhere.
- `local-datasource/src/test/.../InstitutionLocalDataSourceImplTest.kt`, `data/src/test/.../InstitutionRepositoryImplTest.kt` — existing JUnit5 + MockK unit tests (DAO / datasource mocked); natural homes for new tests.
- `app/build.gradle.kts` — no unit-test dependencies and no `app/src/test/`; only androidTest/Compose test deps. Any JVM test of a ViewModel would be the first in `:app` and needs test deps added.
- `docs/decisions/0001-cursor-owned-by-caller.md` — explicitly lists sign-out as a cache-wipe case that self-heals via `cursor: null`.
- `docs/specs/EPIC-001-platform/CREAM-008-room-schema-export.md` — schema export not yet configured; any schema bump currently crashes existing installs. Relevant to the partition-by-uid alternative.
- Anchor commits: `4a494a0` (InstitutionRepository + local datasource), `2efaa04` (Room datasource), `e3adc44` (factories → internal constructors, MockK tests). No ticket keys / PR numbers in those commits.

**Clarifications**

Decisions made after recon (warroom, for the user):

- Orchestration lives in `HomeViewModel`: clear the cache, then call `authRepository.signOut()`. `AuthRepository` is unchanged; neither repository depends on the other.
- Clear the cache rather than partitioning by uid. Partitioning would need a schema bump (blocked on CREAM-008) and would leave a prior user's data on the device; ADR 0001 makes the full resync after a clear safe.
- If the clear fails, sign out anyway. No logging (PR review).
- Sign-outs the user didn't start (token revocation/expiry) are deferred to a follow-up: clear on sign-in when the uid differs from the last one.
- `:app` gets unit-test setup that matches `data/build.gradle.kts` exactly. CREAM-003 adds the same setup in parallel; the overlap is known and trivial.

**Plan**

### Goal

Signing out from the Home screen wipes every cached institution, account, transaction, and sync cursor before the session ends, so the next user starts from an empty cache.

### Approaches considered

- **A. Orchestrate in `HomeViewModel` (chosen).** The ViewModel takes both repositories and runs clear-then-sign-out in `viewModelScope`. Smallest change, one call site. Sign-outs the user didn't start are not covered (deferred).
- **B. Coordinator in `:data`** (e.g. a session class built in `AppContainer` from both repositories). Reusable and keeps repositories independent, but it adds an abstraction for a single caller. Revisit with the uid-change follow-up.
- **C. Clear when `authStateFlow` emits null** (an observer in `AppContainer`/`MainViewModel`). Covers every sign-out path, but races the next sign-in and in-flight sync writes, and hides a destructive side effect behind a state observer.

### Related code

- `local-datasource/src/main/java/com/lucaskivi/cream/institution/InstitutionDao.kt` — add delete-all; `deleteInstitution` is the pattern.
- `local-datasource/src/main/java/com/lucaskivi/cream/institution/FinancialAccountEntity.kt`, `TransactionEntity.kt` — `onDelete = CASCADE` FKs: deleting `linked_institution` rows removes accounts and transactions.
- `local-datasource/src/main/java/com/lucaskivi/cream/institution/InstitutionLocalDataSource.kt`, `InstitutionLocalDataSourceImpl.kt` — the delegation layer to extend.
- `data/src/main/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepository.kt`, `InstitutionRepositoryImpl.kt` — the repository-level clear.
- `core/src/main/java/com/lucaskivi/cream/core/NetworkResult.kt` — `catching {}` (rethrows `CancellationException`) and `onFailure`.
- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeViewModel.kt` — the orchestration site and its factory.
- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeScreen.kt` — `onClick = viewModel::signOut`; the signature stays `fun signOut()`, so no change is needed here.
- `app/build.gradle.kts`, `data/build.gradle.kts` — test setup to copy.
- `local-datasource/src/test/java/com/lucaskivi/cream/institution/InstitutionLocalDataSourceImplTest.kt`, `data/src/test/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepositoryImplTest.kt` — add `@Nested` groups.

### Structural considerations

- **Layering.** Each layer gains one method; data still flows Datasource → Repository → ViewModel, and `:app` still depends only on `:data`.
- **Contracts (names are binding for `/work`):**
  - `InstitutionDao`: `@Query("DELETE FROM linked_institution") suspend fun deleteAllInstitutions()`. The CASCADE FKs remove child rows (Room enables `foreign_keys`, and `deleteInstitution` already relies on this). KDoc: removes every institution along with its accounts, transactions, and stored cursor.
  - `InstitutionLocalDataSource` (+ impl): `suspend fun deleteAllInstitutions()`, which delegates to the DAO.
  - `InstitutionRepository` (+ impl): `suspend fun clearLocalCache(): NetworkResult<Unit>` = `catching { localDataSource.deleteAllInstitutions() }`. It returns a result instead of throwing, matching the repository's result-returning convention for mutations. KDoc: local only, no backend call; linked institutions stay on the backend and repopulate on the next refresh, with a full history resync. Don't name Room in the KDoc.
  - `HomeViewModel`: the constructor gains `private val institutionRepository: InstitutionRepository` (per-line, trailing comma). `signOut()` becomes a block body: `viewModelScope.launch { institutionRepository.clearLocalCache(); authRepository.signOut() }`. The result of the clear is ignored. Update the class and `signOut` KDoc: the cache is cleared first, and sign-out proceeds even if clearing fails. The factory passes `appContainer.institutionRepository`.
- **Lifecycle.** Home leaves composition only after `authStateFlow` emits null, which happens after `authRepository.signOut()`. So `viewModelScope` outlives the clear. A double tap just clears twice (idempotent), so no in-progress guard is needed.
- **Untouched:** `AuthRepository`/`AuthRepositoryImpl`, `AppContainer`, `CreamDatabase` (no schema/version change), `MainViewModel`.

### Implementation plan

- [x] `app/build.gradle.kts`: add `testOptions { unitTests.all { it.useJUnitPlatform() } }` in `android {}`, plus `testImplementation(libs.junit.jupiter)`, `testImplementation(libs.mockk)`, `testImplementation(libs.kotlinx.coroutines.test)`, `testRuntimeOnly(libs.junit.platform.launcher)`, exactly as in `data/build.gradle.kts`.
- [x] DAO `deleteAllInstitutions()` → local datasource interface + impl → repository interface + impl `clearLocalCache()`, with multiline, behavior-first KDoc on each new declaration.
- [x] `HomeViewModel`: constructor parameter, factory wiring, `signOut()` orchestration, `TAG`, KDoc updates.
- [x] Tests (STYLE.md: `@Nested` per method, Setup/Test/Verify, `expected` vals, `confirmVerified` on all collaborators):
  - `InstitutionLocalDataSourceImplTest` › `DeleteAllInstitutions`: delegates to `dao.deleteAllInstitutions()`.
  - `InstitutionRepositoryImplTest` › `ClearLocalCache`: (1) returns `Success(Unit)`, calls `local.deleteAllInstitutions()`, and never calls `remote`; (2) returns `Failure(cause)` when the local call throws.
  - New `app/src/test/java/com/lucaskivi/cream/screens/home/HomeViewModelTest.kt` › `SignOut`:
    - Mock `AuthRepository` with `authStateFlow` stubbed to `emptyFlow()`. `init` reads it, so verify that read too. Mock `InstitutionRepository`.
    - `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@BeforeEach`; `resetMain()` in `@AfterEach`.
    - (1) Clears the cache and signs out. Plain `coVerify` per STYLE.md; call order is not asserted.
    - (2) When the clear returns `Failure`, still signs out.
- [x] `./gradlew :local-datasource:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

### Impact assessment

- Code paths: Home sign-out only.
- Data/schema: no schema change, no CREAM-008 dependency. Sign-out deletes all cached rows and cursors; per ADR 0001 the next sync is a full resync.
- API: additive methods on `InstitutionDao`, `InstitutionLocalDataSource`, `InstitutionRepository`. The `HomeViewModel` constructor changes; its factory is the only caller.

### Validation

- Tests and the Gradle command above.
- Manual (physical Pixel, adb reverse): no linking UI exists yet (CREAM-003/004). Seed rows with Android Studio App Inspection → Database Inspector in `linked_institution`, `financial_account`, and `transaction`. Tap Sign out. Confirm all three tables are empty and the app routes to Login.

### Gaps and follow-up

- Follow-up (new ticket): sign-outs the user didn't start (token revocation/expiry, process death mid-sign-out) skip the clear. Fix: persist the last signed-in uid and clear on sign-in when the new uid differs.
- Gap (accepted): no Room integration test proves the DAO query and CASCADE actually empty the tables, because the tests mock the DAO. The epic's "covered by a test" is met at the delegation level; the cascade is checked manually with Database Inspector. If no device is available, the PR notes the check as pending (no AVD substitute). A Room test harness (Robolectric or androidTest) would be its own Platform ticket.
- Gap: future local tables (e.g. categorization per ADR 0002) must join this clear path.

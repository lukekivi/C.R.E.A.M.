---
id: CREAM-003
title: Add a Compose Plaid Link launcher
status: done
created: 2026-10-19
depends-on: [CREAM-001]
---

A Compose helper that launches Plaid Link with a link token and returns an SDK-free result, so ViewModels never import Plaid types.

## Scope

- `app/.../screens/institutions/PlaidLinkLauncher.kt` — `rememberPlaidLinkLauncher(onResult: (PlaidLinkResult) -> Unit)` returning a handle with `launch(linkToken: String)`. Built on `rememberLauncherForActivityResult` with the SDK's activity-result contract.
- `app/.../screens/institutions/PlaidLinkResult.kt` — sealed: `Success(publicToken, institutionId, institutionName)`, `Cancelled`, `Error(message)`. Translate the SDK's result into this type at the UI boundary.

## Out of scope

- The Institutions screen and ViewModel that use it (CREAM-004).

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- `rememberPlaidLinkLauncher(onResult: (PlaidLinkResult) -> Unit)` exists in `app/.../screens/institutions/PlaidLinkLauncher.kt` and returns a handle exposing `launch(linkToken: String)`.
- Calling `launch` with a valid link token opens Plaid Link through `rememberLauncherForActivityResult` and the SDK's activity-result contract.
- `PlaidLinkResult` is a sealed type in `app/.../screens/institutions/PlaidLinkResult.kt` with `Success(publicToken, institutionId, institutionName)`, `Cancelled`, and `Error(message)`.
- Every SDK result reaches `onResult` as exactly one `PlaidLinkResult`: a successful link becomes `Success`, an exit without an error becomes `Cancelled`, and an exit with an error becomes `Error`.
- No Plaid SDK type appears in the public signature of the launcher, its handle, or `PlaidLinkResult`, so a ViewModel can consume them without importing `com.plaid.*`.
- New declarations follow STYLE.md (multiline KDoc on every declaration, `@param` tags).
- `./gradlew :app:assembleDebug` passes with CREAM-001's `com.plaid.link:sdk-core` dependency in place.

**Reconnaissance findings**

- `app/src/main/java/com/lucaskivi/cream/screens/institutions/`: doesn't exist yet. This ticket creates the package, and CREAM-004 builds the screen in it.
- `app/build.gradle.kts`: `:app` has `activity-compose` (1.12.4) for `rememberLauncherForActivityResult`. The Plaid dependency arrives with CREAM-001. `:app` has no unit-test dependencies (no JUnit 5 or MockK), only `androidTest`.
- `app/src/main/java/com/lucaskivi/cream/MainActivity.kt`: the host is a plain `ComponentActivity`, not AppCompat.
- `data/src/main/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepository.kt:49`: `linkInstitution(publicToken: String, institutionId: String, institutionName: String)` is the downstream consumer of `PlaidLinkResult.Success`, used in CREAM-004. Both institution fields are non-null there. Last touched in 4a494a0.
- `docs/specs/EPIC-003-plaid/CREAM-001-plaid-link-sdk-setup.md` is the dependency ticket. It names `5.5.1` as the version from the old plan.
- STYLE.md sections that apply: Documentation, Packages (whether `model` subpackages apply to `:app` UI types is for `/plan` to decide), Tests.
- Current Plaid Link Android SDK, researched 2026-11-06:
  - The latest release is `6.4.0` (2026-10-23). The 5.x line is still patched (`5.5.6`, 2026-10-14).
  - Version 6.0.0 replaced the handler API with a session API. `Plaid.create`, `PlaidHandler`, `FastOpenPlaidLink`, and `LinkResultHandler` were removed, with no deprecation shims.
  - The v6 contract is `OpenPlaidLink : ActivityResultContract<PlaidSession, LinkResult>`.
  - A session comes from `Plaid.createPlaidLinkSession(context, linkTokenConfiguration { token = ... })`. It is single-use, so create a new one per open. Creating it early preloads Link, and an optional `onLoad` callback reports when loading finishes.
  - Results are `LinkSuccess`, with `publicToken` and `metadata.institution?.id` / `?.name`, and `LinkExit`, with `error?` (`errorCode`, `errorMessage`, `displayMessage`) and `metadata`.
  - No client-side SDK initialization is documented.
  - 6.4.0 dropped the AppCompat dependency, which suits a Compose-only app. The minimum SDK is 25, below the app's 30.
  - On 5.x, the API is `Plaid.create(application, config)` → `PlaidHandler` with the `OpenPlaidLink` / `FastOpenPlaidLink` contracts.
  - Sources: https://plaid.com/docs/link/android/ , https://raw.githubusercontent.com/plaid/plaid-link-android/master/v6-migration-guide.md , https://github.com/plaid/plaid-link-android/releases , Maven Central `com.plaid.link:sdk-core` metadata.

**Clarifications**

- The ticket listed files but gave no explicit done-conditions, so the acceptance criteria above were derived from its Scope. That was a Partial at the start gate.
- **The SDK version decides the contract API.** This ticket's code is written against whichever `sdk-core` version CREAM-001 pins. The v6 session API and the v5 handler API don't overlap. `/plan` must read CREAM-001's pinned version (or the merged `libs.versions.toml`) before designing anything.
- **Institution metadata is nullable in the SDK, but `Success` and `InstitutionRepository.linkInstitution` need non-null `institutionId` / `institutionName`.** It's undecided how a success without institution metadata should be represented. Unresolved, and needs a human decision or `/plan` judgment.
- **The source of the `Error.message` text is undecided:** the SDK's `errorMessage` (developer-facing) or `displayMessage` (user-facing, may be null). Unresolved.
- **Test expectations are unstated.** The SDK-to-`PlaidLinkResult` mapping is the testable unit, but `:app` has no unit-test dependencies today. `/plan` decides whether to add tests and the dependencies they need, or to skip tests for this ticket.

**Plan**

### Goal

A Compose launcher, `rememberPlaidLinkLauncher`, that opens Plaid Link with a link token and reports the outcome as an SDK-free `PlaidLinkResult`. The SDK-to-`PlaidLinkResult` mapping is unit-tested.

### Decisions folded in (from warroom, 2026-11-06)

- **SDK version:** `sdk-core` **6.4.0**, which CREAM-001 adds. This branch stacks on CREAM-001, so the plan targets the v6 session API (`Plaid.createPlaidLinkSession` → `OpenPlaidLink` → `LinkResult`). Don't touch the dependency here.
- **Success with no institution data maps to `PlaidLinkResult.Error`.** `Success` fields stay non-null.
- **Error message:** use `displayMessage`, else `errorMessage`, else a generic fallback. A blank string counts as absent, because the SDK's `errorMessage` is non-null and could otherwise never fall through to the fallback.
- **Tests:** add unit-test setup to `:app` that is identical to what CREAM-006 ships (PR #8): `data/build.gradle.kts`'s setup. Then test the mapping. Because both PRs make the same edit, the conflict stays trivial.

### Approaches considered

1. **Recommended: an internal extension function `LinkResult.toPlaidLinkResult()` in `PlaidLinkLauncher.kt`.**
   - Pure and needs no injection, so it's directly unit-testable.
   - Plaid imports stay in the one file that already depends on the SDK, so `PlaidLinkResult.kt` imports nothing from Plaid.
2. **A `PlaidLinkResultTransformer` class**, per STYLE "Transformers".
   - Rejected: that convention covers datasource impls that inject a mock-able transformer. Nothing here is injected or mocked, so a class adds ceremony without buying anything.
3. **Put the mapping in `PlaidLinkResult.kt`**, as the ticket's wording hints.
   - Rejected: it would put Plaid imports into the file ViewModels depend on. It isn't a leak, but it muddies the "SDK-free type" boundary.

### Related code

- `app/build.gradle.kts`: gets the unit-test setup.
- `data/build.gradle.kts`: the test setup to mirror (`testOptions { unitTests.all { it.useJUnitPlatform() } }`, plus `testImplementation` of junit-jupiter, mockk and kotlinx-coroutines-test, and `testRuntimeOnly(libs.junit.platform.launcher)`).
- `gradle/libs.versions.toml`: the test catalog entries already exist. CREAM-001 adds `plaid` here.
- `app/src/main/java/com/lucaskivi/cream/MainActivity.kt`: a `ComponentActivity`, so `LocalContext` resolves to an activity, matching Plaid's sample that passes the activity.
- `data/src/main/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepository.kt:49`: `linkInstitution(publicToken, institutionId, institutionName)` consumes `Success` in CREAM-004, which is why the fields are non-null.
- `data/src/test/java/com/lucaskivi/cream/data/repository/institution/InstitutionRepositoryImplTest.kt`: reference for test layout (`@Nested` per method, Setup/Test/Verify sections).
- STYLE.md: multiline KDoc with `@param` on every declaration; Tests section.

### SDK facts (from the `sdk-core-6.4.0` classes, checked with javap)

- `OpenPlaidLink : ActivityResultContract<PlaidSession, LinkResult>`, with a no-arg constructor.
- `Plaid.createPlaidLinkSession(Context, LinkTokenConfiguration): PlaidLinkSession`. Build the configuration with `linkTokenConfiguration { token = … }`.
- `PlaidSession` exposes `open(Activity)` and `destroy()`. Docs say sessions are single-use and say nothing about cleanup.
- `LinkResult` is a Kotlin sealed interface (`LinkSuccess`, `LinkExit`), so the `when` over it is exhaustive.
- `LinkSuccess(publicToken: String, metadata: LinkSuccessMetadata)`, where `metadata.institution` is a **nullable** `LinkInstitution(id: String, name: String)`.
- `LinkExit(error: LinkError?, metadata: LinkExitMetadata)`.
- `LinkError(errorCode, errorMessage: String, displayMessage: String?, errorJson: String?)`.
- The result classes' constructors are Kotlin-`internal`, so tests use `mockk` stand-ins for them.

### Structural considerations

- **Placement:** both new types live in `com.lucaskivi.cream.screens.institutions` in `:app`. CREAM-004 builds the screen there.
  - The SDK is an `:app` concern only, which respects the architecture (`:data` and the datasource modules never see Plaid).
  - Don't create a `model` subpackage. STYLE's model rule covers data-carrying types moving between layers, and this one is a UI-boundary type in `:app`.
- **`PlaidLinkResult.kt`:**
  - `sealed interface PlaidLinkResult` with `data class Success(publicToken: String, institutionId: String, institutionName: String)`, `data object Cancelled` and `data class Error(message: String)`.
  - No Plaid imports.
- **`PlaidLinkLauncher.kt`:**
  - `class PlaidLinkLauncher internal constructor(...)` holds a `Context` and a `ManagedActivityResultLauncher<PlaidSession, LinkResult>`, both private.
  - It exposes only `fun launch(linkToken: String)`, which builds `linkTokenConfiguration { token = linkToken }`, creates a new `Plaid.createPlaidLinkSession(context, config)` per call, and launches it. Plaid types appear only in the internal constructor and private members.
  - `@Composable fun rememberPlaidLinkLauncher(onResult: (PlaidLinkResult) -> Unit): PlaidLinkLauncher`:
    - Reads `LocalContext.current`.
    - Calls `rememberLauncherForActivityResult(OpenPlaidLink()) { onResult(it.toPlaidLinkResult()) }`. That API already wraps `onResult` in `rememberUpdatedState`, so a stale lambda isn't a concern.
    - Returns `remember(context, launcher) { PlaidLinkLauncher(context, launcher) }`.
  - `internal fun LinkResult.toPlaidLinkResult(): PlaidLinkResult` maps:
    - `LinkSuccess` with an institution → `Success`.
    - `LinkSuccess` with no institution → `Error(fallback)`.
    - `LinkExit` with no error → `Cancelled`.
    - `LinkExit` with an error → `Error(displayMessage, else errorMessage, else fallback, with blanks skipped)`.
  - The fallback is an `internal const val` English string. That matches the existing ViewModels, which surface raw `Throwable.message` strings rather than resources.
- **Session lifecycle:** create the session at `launch` time with no preloading (YAGNI). Don't call `destroy()`, because the docs only say a session is single-use. Preloading via `onLoad` is a follow-up if Link opens too slowly.
- **PHAME:** the dependency direction is unchanged. The abstraction is a single boundary function. Encapsulation holds because the SDK is confined to one file and an internal constructor.

### Implementation plan

- [x] **Test setup.** Add to `app/build.gradle.kts`, identical to CREAM-006's (PR #8) lines:
  - `testOptions { unitTests.all { it.useJUnitPlatform() } }` inside `android {}`.
  - `testImplementation(libs.junit.jupiter)`, `testImplementation(libs.mockk)`, `testImplementation(libs.kotlinx.coroutines.test)` and `testRuntimeOnly(libs.junit.platform.launcher)`.
- [x] **`PlaidLinkResult.kt`.** Write the sealed interface and its three variants as described above, with full KDoc.
- [x] **`PlaidLinkLauncher.kt`.**
  - Write the `PlaidLinkLauncher` handle, `rememberPlaidLinkLauncher` and `LinkResult.toPlaidLinkResult()` as described above, with full KDoc including `@param`.
  - Import `com.plaid.link.Plaid`, `com.plaid.link.OpenPlaidLink`, `com.plaid.link.PlaidSession`, `com.plaid.link.configuration.linkTokenConfiguration` and `com.plaid.link.result.*`.
- [x] **`app/src/test/java/com/lucaskivi/cream/screens/institutions/PlaidLinkLauncherTest.kt`.**
  - Use `@Nested inner class ToPlaidLinkResult` and Setup/Test/Verify sections, with constructed expected values in `expected` vals.
  - Use `mockk` stand-ins for the SDK result types, because their constructors are internal.
  - No collaborators, so no `confirmVerified`.
  - Cases:
    - success with an institution → `Success`.
    - success with a null institution → `Error(fallback)`.
    - exit with no error → `Cancelled`.
    - exit with an error and a `displayMessage` → uses `displayMessage`.
    - a null or blank `displayMessage` → uses `errorMessage`.
    - a blank `errorMessage` with no `displayMessage` → fallback.
  - The fallback is an `internal const val`, with KDoc, so tests can assert it.
- [x] **Ticket status.** Set the ticket's frontmatter `status:` to `done` in the PR, per `docs/work-tracking.md`.

### Impact assessment

- Code paths: only new files, plus `app/build.gradle.kts`. No caller exists until CREAM-004.
- Data and schema: none.
- Dependencies: test-only additions to `:app`. Plaid comes via CREAM-001.

### Validation

- Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` (requires CREAM-001 underneath).
- Run the style review per `docs/style-review.md`.
- Manual: there's no UI entry point until CREAM-004. Optionally wire a throwaway button on the physical Pixel to launch with a sandbox link token, then revert it before committing. The end-to-end check belongs to CREAM-007.

### Risks and follow-up

- **Kotlin metadata, a CREAM-001 risk.** `sdk-core` 6.4.0 is built against kotlin-stdlib 2.3.10. If the app's effective Kotlin compiler (AGP 9 built-in, catalog `kotlin = 2.0.21`) is too old to read 2.3 metadata, compilation fails. CREAM-001's `assembleDebug` check will surface this first.
- **Follow-up:** preloading the session (`onLoad`) to shorten the time Link takes to open, if needed.

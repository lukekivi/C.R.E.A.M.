---
id: CREAM-001
title: Add the Plaid Link Android SDK
status: todo
created: 2026-10-19
---

Add the Plaid Link Android SDK so the app can launch Plaid Link. Prerequisite for the Link launcher (CREAM-003).

## Scope

- Add `com.plaid.link:sdk-core` to `gradle/libs.versions.toml` (latest stable; the original plan noted `5.5.1` as of 2026-04) and to `:app/build.gradle.kts`.
- Perform any SDK initialization the current Plaid docs require in `CreamApplication.onCreate()`, after Firebase init.
- `./gradlew :app:assembleDebug` passes.

## Out of scope

- Launcher composable and result mapping (CREAM-003).
- Emulator wiring (CREAM-002).

## Notes

- The original plan called for `PLAID_PUBLIC_KEY` / `PLAID_ENV` BuildConfig fields. Plaid's link-token flow likely needs no client-side key, and the environment is decided server-side by `PLAID_ENV` when the link token is created. Verify against current Plaid docs before adding any BuildConfig fields; drop them if unneeded.

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- `com.plaid.link:sdk-core` is declared in `gradle/libs.versions.toml` at the latest stable version and consumed by `:app/build.gradle.kts` via the version catalog.
- No `PLAID_PUBLIC_KEY` / `PLAID_ENV` (or any other Plaid) BuildConfig fields are added. Current Plaid docs need no client-side key or environment (see findings).
- `CreamApplication.onCreate()` gets no Plaid initialization, because current Plaid docs require none. If `/plan` finds otherwise, any init goes after `appContainer` setup.
- `./gradlew :app:assembleDebug` passes.

**Reconnaissance findings**

- `gradle/libs.versions.toml` — no Plaid entry yet. Catalog style is `[versions]` ref + `[libraries]` entry.
- `app/build.gradle.kts` — the only consumer. `:data` and the datasources do not need the SDK.
- `app/src/main/java/com/lucaskivi/cream/CreamApplication.kt` — `onCreate()` only builds `AppContainer`. There is no explicit Firebase init; Firebase auto-initializes through its content provider. The ticket's "after Firebase init" therefore has no concrete line to anchor to.
- `settings.gradle.kts` — `mavenCentral()` is already in `dependencyResolutionManagement`, so no repository change is needed.
- `app/src/main/AndroidManifest.xml` — the `INTERNET` permission is already present. Plaid's camera/audio permissions are needed only for Identity Verification, which is out of scope.
- **Plaid docs (plaid.com/docs/link/android, checked 2026-11-06):**
  - The artifact is `com.plaid.link:sdk-core`.
  - No Application-level initialization is described.
  - The client receives only a `link_token`, which the backend creates with `/link/token/create`. There is no public key or environment on the client; the environment is decided server-side.
  - SDK 6.0 replaced the handler-based API (`Plaid.create`/`PlaidHandler`) with a session-based API (`Plaid.createPlaidLinkSession`, `LinkTokenConfiguration`). The `OpenPlaidLink` activity-result contract remains. This matters for CREAM-003.
- **Maven Central:** latest is `6.4.0`, published 2026-10-23. The previous release is `6.3.1`, published 2026-10-21. The ticket's `5.5.1` is a major version stale.
- **6.4.0 transitive dependencies:** `kotlin-stdlib` 2.3.10, `kotlinx-serialization-json` 1.10.0, `kotlinx-coroutines-android` 1.10.2, `lifecycle-*` 2.10.0, `activity-ktx` 1.12.4, `core-ktx` 1.16.0, and okhttp 4.12.0.
  - The build's resolved KGP is 2.2.10 (AGP 9 built-in Kotlin), even though the catalog's `kotlin = "2.0.21"`. A 2.2 compiler can read 2.3 metadata (one version forward compatibility), so this should compile. `assembleDebug` is the check.
  - The SDK will bump the app's coroutines version from 1.9.0 to 1.10.2 through dependency resolution.

**Clarifications**

- Version pick: `6.4.0` was published 14 days before this recon, which sits right at the two-week stability window. `6.3.1` is the conservative alternative. `/plan` decides.
- The 6.x session-based API differs from what the epic's original plan assumed (5.x). CREAM-003's scope text ("the SDK's activity-result contract") still holds via `OpenPlaidLink`, but whoever plans CREAM-003 should know about the change. This is adjacent scope, not part of this ticket.

**Plan**

*Goal.* Put the Plaid Link Android SDK on `:app`'s classpath so CREAM-003 can build its launcher on the v6 session API (`Plaid.createPlaidLinkSession` / `OpenPlaidLink`). This ticket adds no runtime behavior.

*Decisions (settled by the user/warroom, 2026-11-06)*

- Pin `com.plaid.link:sdk-core` at **6.4.0**. If the build breaks, don't swap in another version; report it.
- Add **no** Application-level init. `CreamApplication.kt` is not touched.
- Add **no** `PLAID_PUBLIC_KEY` / `PLAID_ENV` BuildConfig fields, and do not enable `buildConfig`. The link-token flow decides the environment server-side.

*Approaches considered*

1. Version-catalog entry plus `implementation(libs.plaid.link.sdk.core)` in `:app`. This is the chosen approach: it matches every other dependency in the repo.
2. A hard-coded coordinate in `app/build.gradle.kts`. Rejected because it breaks the catalog convention.
3. Putting the SDK in `:data` or `:remote-datasource`. Rejected for two reasons:
   - Link is an Activity/UI concern, and the epic places the launcher in `:app/.../screens/institutions/`.
   - Pushing it down would leak an Android UI SDK into the data layers. The `:app` → `:data`-only rule is about project modules, so an external UI SDK in `:app` is consistent with it (precedent: `androidx.credentials` and `googleid` live in `:app`).

*Structural considerations*

- Hierarchy is unchanged: there are no new module edges.
- Encapsulation: Plaid types stay confined to `:app`, and CREAM-003 further confines them to the UI boundary.
- The SDK raises runtime transitive dependencies on `:app`: `kotlin-stdlib` 2.3.10 and `kotlinx-coroutines` 1.9.0 → 1.10.2. The other modules still compile against the catalog's coroutines 1.9.0, and Gradle resolves the app's runtime to 1.10.2. That is binary-compatible within the 1.x line, so no catalog bump is needed.

*Related code*

- `gradle/libs.versions.toml` — add `plaidLink = "6.4.0"` under `[versions]` and `plaid-link-sdk-core = { group = "com.plaid.link", name = "sdk-core", version.ref = "plaidLink" }` under `[libraries]`.
- `app/build.gradle.kts` — add `implementation(libs.plaid.link.sdk.core)` alongside the other third-party `implementation` lines, before `project(":data")`.
- `settings.gradle.kts` — no change, because `mavenCentral()` is already present.
- `app/src/main/AndroidManifest.xml` — no change. The SDK manifest merges its own entries, and the camera/audio permissions are only for Identity Verification, which is out of scope.

*Implementation plan*

- [x] Add the version and library entries to `gradle/libs.versions.toml`.
- [x] Add the `implementation` line to `app/build.gradle.kts`.
- [x] Run `./gradlew :app:assembleDebug`. This is the gate for the kotlin-stdlib 2.3.10 metadata risk:
  - The build's resolved KGP is 2.2.10, which should read 2.3 metadata.
  - If it fails with a metadata or "incompatible Kotlin version" error, **stop and report** the exact error. Do not change the SDK version or bump Kotlin/AGP without a decision.
- [x] Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "plaid|kotlinx-coroutines-core|kotlin-stdlib:"` and record the resolved versions in the PR description. This confirms the coroutines 1.10.2 bump is the only notable runtime change.
- [x] Check the manifest merge: open `app/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml` and note any Plaid-contributed permissions. FYI only; 6.4.0 dropped `READ_BASIC_PHONE_STATE`.

*Validation*

- `./gradlew :app:assembleDebug` passes. This is the acceptance gate.
- `./gradlew test` still passes. This is a sanity check that the coroutines runtime bump doesn't disturb existing unit tests.
- Manual check: install the debug build and launch it on the Pixel to confirm there is no startup crash. Optional, since there is no new code path.

*Gaps and follow-up*

- CREAM-003 (stacked on this branch) consumes the SDK through the v6 session API. Its spec text predates v6 and should be read with that in mind.


---
id: CREAM-002
title: Point debug builds at the Firebase emulators and verify the callables
status: done
created: 2026-10-19
---

The five Plaid callables in `functions/` exist but have never been exercised from the app. Wire debug builds to the local Firebase emulators and confirm each callable works end to end against Plaid Sandbox.

## Scope

- Debug builds only: call `FirebaseFunctions.useEmulator("10.0.2.2", 5001)` (and the Auth/Firestore emulators if needed for the callables' auth context). Release builds hit deployed Functions.
- Keep the emulator switch out of `:app` business code — it belongs where `FirebaseFunctions` is obtained in `:remote-datasource`, or in a debug-only init path.
- Confirm `functions/.secret.local` setup per `functions/README.md` and that `firebase emulators:start --only functions,firestore,auth` runs.
- Smoke-test each callable from the app or a test harness: `createLinkToken`, `exchangePublicToken`, `listInstitutions`, `syncTransactions`, `disconnectInstitution`.

## Out of scope

- Deploying Functions or setting production secrets.
- CI.

## Notes

- `firestore.rules` must deny client reads/writes to `users/{uid}/items/*` — verify while here.

## 🤖 kivi-sdlc spec

**Stage:** recon ✓ · plan ✓ · work ✓

**Acceptance criteria**

- Debug builds route `FirebaseFunctions` calls to the local emulator at `10.0.2.2:5001`; release builds hit deployed Functions unchanged.
- The emulator switch lives in `:remote-datasource` (where `FirebaseFunctions` is obtained) or a debug-only init path — not in `:app` business code.
- Debug builds can reach the host emulator over plain HTTP (cleartext permitted for debug only); release keeps default network security.
- `npm run serve` (`firebase emulators:start --only functions,firestore,auth`) starts cleanly with `functions/.secret.local` per `functions/README.md`.
- Each callable — `createLinkToken`, `exchangePublicToken`, `listInstitutions`, `syncTransactions`, `disconnectInstitution` — succeeds once end to end from the Android client against the emulators + Plaid Sandbox, signed in. (Harness shape is an open question; see Clarifications.)
- `firestore.rules` confirmed to deny client reads/writes to `users/{uid}/items/*`.
- `./gradlew :app:assembleDebug :app:assembleRelease` pass.

**Reconnaissance findings**

- `remote-datasource/src/main/java/com/lucaskivi/cream/institution/RemoteDataSourceFactory.kt` — `institutionRemoteDataSource()` calls `FirebaseFunctions.getInstance()` directly; the natural seam for emulator selection. No emulator hook exists anywhere.
- `remote-datasource/src/main/java/com/lucaskivi/cream/auth/AuthDataSourceFactory.kt` — `authDataSource()` calls `FirebaseAuth.getInstance()`; relevant if Auth is also emulated.
- `remote-datasource/build.gradle.kts` — library module; no `debug` source set, no `buildConfig` feature enabled. AGP 9: do not apply `kotlin.android`.
- `remote-datasource/src/main/java/com/lucaskivi/cream/institution/InstitutionRemoteDataSourceImpl.kt` — client side of all five callables already implemented; callable names in companion constants.
- `data/src/main/java/com/lucaskivi/cream/data/di/AppContainer.kt` — calls the factories with no arguments; `CreamApplication.onCreate()` builds `AppContainer`.
- `app/src/main/AndroidManifest.xml` — no `networkSecurityConfig` / `usesCleartextTraffic`; `app/src/main/res/xml/` has only backup/extraction rules. Cleartext to `10.0.2.2` will be blocked on API 28+ (minSdk 30) without a debug config.
- `app/build.gradle.kts` — only `release` build type declared; no `app/src/debug/` exists.
- Client has no Firestore dependency (`gradle/libs.versions.toml`: bom 33.7.0, auth, functions-ktx only) — only Functions (and possibly Auth) emulators matter client-side; Firestore emulator is used by the Admin SDK inside the Functions emulator.
- `firebase.json` — emulator ports auth 9099, functions 5001, firestore 8080, UI enabled, `singleProjectMode`. Project `cream-5eeda` (`.firebaserc`).
- `firestore.rules` — already `match /{document=**} { allow read, write: if false; }`; note in ticket is satisfied by inspection, needs only confirmation.
- `functions/.secret.local` — present, contains all four keys (`PLAID_CLIENT_ID`, `PLAID_SECRET_SANDBOX`, `PLAID_SECRET_PRODUCTION`, `PLAID_ENV`). Values not inspected.
- `functions/package.json` — `serve` script = `tsc` + `emulators:start --only functions,firestore,auth`.
- `functions/src/auth.ts` — every callable calls `requireAuth`; unauthenticated calls fail with `unauthenticated`.
- `functions/README.md` — documents emulator workflow, `10.0.2.2` mapping, and `sandbox/public_token/create` curl for minting a `public_token` without Plaid Link.
- Anchors: `f5fad07` (functions backend), `fa88557` (remote datasource), `04628af` (internal constructors), `b87d618` (latest touch). No PRs or external tickets.

**Clarifications**

- **Auth emulator vs. production Auth — left to /plan.** Unresolved whether debug builds should also call `FirebaseAuth.useEmulator("10.0.2.2", 9099)` or keep production Auth with only Functions emulated. `/plan` should verify whether the Functions emulator accepts production-issued ID tokens while the Auth emulator is running, and how Google Sign-In (Credential Manager) behaves against the Auth emulator, before choosing.
- **Smoke-test harness shape — left to /plan.** Options discussed: (a) throwaway debug-only trigger reverted before merge, (b) committed debug-source-set screen with one button per callable (reusable for CREAM-004–007; overlaps with CREAM-004), (c) instrumented `androidTest` against the emulators. User did not pick; `/plan` decides.
- **`exchangePublicToken` input without Plaid Link.** CREAM-001/003 (Plaid Link SDK + launcher) are not done, so the app cannot produce a `public_token` itself. The README's `sandbox/public_token/create` curl is the available source.
- Ticket's `firestore.rules` note appears already satisfied by the existing default-deny rule.

**Plan**

_Goal:_ debug builds call the local Functions emulator (`10.0.2.2:5001`) and each of the five callables is exercised once end to end against Plaid Sandbox. Release builds are unchanged.

_Decisions_

- **Production Auth, emulate Functions only.** `firebase-tools` 15.15.0 runs callables with `skipTokenVerification: true` (`lib/emulator/functionsEmulator.js:990`; consumed in `firebase-functions/lib/common/providers/https.js:324`). The emulator therefore decodes a production-issued ID token without verifying it, and `request.auth.uid` is filled in. No `FirebaseAuth.useEmulator`, and Google Sign-In / Credential Manager is untouched. The Auth emulator still starts via `npm run serve`; it's harmless and unused by the client.
- **Switch via a `BuildConfig` field in `:remote-datasource`.** Use `USE_FUNCTIONS_EMULATOR`: `true` for debug, `false` for release, applied in the factory where `FirebaseFunctions` is obtained. One seam, no `:app` business code involved. A debug/release source-set split was rejected: it adds two files to swap one line.
- **Cleartext via a debug-only network security config in `:app`.** Network policy is app-level, so it goes in `app/src/debug/`. The release manifest gets no `networkSecurityConfig`.
- **Throwaway harness (user's choice).** A temporary hook runs all five callables in sequence and logs the results. It is removed before merge, and the results are recorded in this ticket. CREAM-004 and CREAM-007 cover the real UI paths later.

_Implementation_

- [x] `remote-datasource/build.gradle.kts`: add `buildFeatures { buildConfig = true }` and set `buildConfigField("boolean", "USE_FUNCTIONS_EMULATOR", ...)` in `buildTypes { debug { "true" } release { "false" } }`. Don't apply `kotlin.android` (AGP 9).
- [x] `remote-datasource/.../institution/RemoteDataSourceFactory.kt`: replace `FirebaseFunctions.getInstance()` with a private `functions()`, as shown below. Add KDoc for `functions()` and the two private constants (`FUNCTIONS_EMULATOR_HOST = "10.0.2.2"`, `FUNCTIONS_EMULATOR_PORT = 5001`). Reword the factory KDoc, since it no longer always returns the "production" instance.
  ```kotlin
  fun institutionRemoteDataSource(): InstitutionRemoteDataSource =
      InstitutionRemoteDataSourceImpl(
          functions = functions(),
      )

  private fun functions(): FirebaseFunctions =
      FirebaseFunctions.getInstance()
          .apply { if (BuildConfig.USE_FUNCTIONS_EMULATOR) useEmulator(FUNCTIONS_EMULATOR_HOST, FUNCTIONS_EMULATOR_PORT) }
  ```
  `BuildConfig` is `com.lucaskivi.cream.remote.BuildConfig` (the module namespace). The factory is called exactly once, from `AppContainer`, before any call, so `useEmulator` runs ahead of first use.
- [x] `app/src/debug/res/xml/network_security_config.xml`: add a `<domain-config cleartextTrafficPermitted="true">` with `<domain includeSubdomains="false">10.0.2.2</domain>`.
- [x] `app/src/debug/AndroidManifest.xml`: `<application android:networkSecurityConfig="@xml/network_security_config" />`. The manifest merger adds it for debug only.
- [x] `functions/README.md` § Local emulator workflow, step 3: replace "(Phase 3)" with what is now true. Debug builds use the emulator automatically, production Auth tokens are accepted because the emulator skips token verification, and only the Android emulator is supported (`10.0.2.2`). Keep the README caller-agnostic where it describes the server. This step already names the Android client, so a short client note is acceptable here; don't spread client detail elsewhere.
- [x] Confirm `functions/.secret.local` has the four keys and `PLAID_ENV=sandbox`. Run `npm run serve` from `functions/` and check that the Functions (5001), Firestore (8080), and Auth (9099) emulators all start and that the five callables are listed.
- [x] Confirm `firestore.rules`: the default `match /{document=**} { allow read, write: if false; }` already covers `users/{uid}/items/*`. Check that `firebase.json` points the Firestore emulator at it. Inspection only, no change.

_Throwaway smoke test (not merged)_

- [x] Temporary `data/src/main/java/com/lucaskivi/cream/data/debug/CallableSmokeTest.kt`: `suspend fun runCallableSmokeTest(publicToken: String)` calls `institutionRemoteDataSource()` and runs these steps in order, logging each `NetworkResult` with `Log.d("CallableSmokeTest", …)`:
  1. `createLinkToken()` → expect `link-sandbox-…`
  2. `exchangePublicToken(publicToken, "ins_109508", "First Platypus Bank")` → `itemId`
  3. `listInstitutions()` → one institution with accounts and balances
  4. `syncTransactions(itemId, null)` → non-empty `added`, cursor
  5. `syncTransactions(itemId, cursor)` → empty-ish delta
  6. `disconnectInstitution(itemId)` → `Unit`
  7. `listInstitutions()` → empty

  It lives in `:data` because `:app` can't see `:remote-datasource`, and `InstitutionRepository` has no `createLinkToken` (that's CREAM-004).
- [x] Temporary call in `HomeViewModel.init`: `viewModelScope.launch { runCallableSmokeTest("<public_token>") }`. The token comes from the README's `sandbox/public_token/create` curl and expires after about 30 minutes, so mint it right before running.
- [x] Run on an emulator while signed in. Read the results with `adb logcat -s CallableSmokeTest`. In the Emulator UI Firestore tab, check that `users/{uid}/items/{itemId}` appears after step 2 and is gone after step 6.
- [x] Record the pass/fail of each callable under a **Smoke-test results** note in this ticket.
- [x] Delete `CallableSmokeTest.kt` and the `HomeViewModel` call. `git diff main --stat` must show neither.

**Change after the first run:** the shipped host moved from `10.0.2.2` to `127.0.0.1` with `adb reverse tcp:5001 tcp:5001`. The primary dev device is physical and can't reach `10.0.2.2`. `adb reverse` works on both physical devices and the Android emulator. Where the plan and acceptance criteria above say `10.0.2.2`, read `127.0.0.1` + `adb reverse`.

**Smoke-test results** (2026-10-20, physical Pixel 9 Pro via `adb reverse tcp:5001 tcp:5001`. Production Auth sign-in, emulators + Plaid Sandbox. The first run used a temporary `127.0.0.1` host; the rerun used the shipped config.)

| Step | Callable | Result |
|---|---|---|
| 1 | `createLinkToken` | PASS: `link-sandbox-…` |
| 2 | `exchangePublicToken` | PASS: `itemId` returned for First Platypus Bank |
| 3 | `listInstitutions` | PASS: one institution with accounts and balances |
| 4 | `syncTransactions(null)` | PASS after fix: 51 added, cursor returned. First run FAILED with an NPE: the client parser read `id`, but the server sends `transactionId` (in both `Txn` and `removed[]`). Fixed in the response parsing (now `InstitutionTransformer`) and its tests, folded into the commit that introduced it. |
| 5 | `syncTransactions(cursor)` | PASS: 0/0/0 delta |
| 6 | `disconnectInstitution` | PASS |
| 7 | `listInstitutions` | PASS: empty, so the item doc was removed |

Rerun on the shipped `127.0.0.1` + `adb reverse` config: all 7 steps PASS (51 added on the initial sync, empty delta on the second).

Harness and temporary host/cleartext tweaks were removed. `firestore.rules` default-deny covers `users/{uid}/items/*`, and `firebase.json` points at it.

_Validation_

- `./gradlew :app:assembleDebug :app:assembleRelease :remote-datasource:testDebugUnitTest`
- Release isolation: `apkanalyzer manifest print app/build/outputs/apk/release/app-release-unsigned.apk` shows no `networkSecurityConfig`. The generated `remote-datasource/build/generated/source/buildConfig/release/.../BuildConfig.java` has `USE_FUNCTIONS_EMULATOR = false`.
- Smoke-test results recorded as above.

_Related code_

- `remote-datasource/src/main/java/com/lucaskivi/cream/institution/RemoteDataSourceFactory.kt`: the seam where `FirebaseFunctions` is obtained.
- `remote-datasource/build.gradle.kts`: needs `buildConfig` enabled and the per-build-type field.
- `app/src/main/AndroidManifest.xml`: base manifest that the debug overlay merges into.
- `data/src/main/java/com/lucaskivi/cream/data/di/AppContainer.kt`: the factory's only caller, constructed once in `CreamApplication.onCreate()`.
- `app/src/main/java/com/lucaskivi/cream/screens/home/HomeViewModel.kt`: temporary harness trigger.
- `functions/README.md`: emulator workflow docs and the public-token curl.
- `firebase.json`, `firestore.rules`: emulator ports and the default-deny rule.

_Gaps / follow-up_

- ~~Physical devices can't reach `10.0.2.2`.~~ Resolved in this ticket: the host is `127.0.0.1` + `adb reverse`.
- Debug sign-ins create real users in production Auth (`cream-5eeda`). This is acceptable for solo dev; revisit if the Auth emulator is ever adopted.

_Open questions_

None.

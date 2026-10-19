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

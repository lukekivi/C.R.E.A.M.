---
id: EPIC-003
title: Link institutions through Plaid
status: todo
created: 2026-10-19
---

Let the user link, view, refresh, and disconnect institutions through Plaid and drill into an account's transactions, end to end from the app to the Plaid callables. Debug builds reach the callables on the local Functions emulator, the UI launches Plaid Link without ViewModels importing Plaid types, and one user's cached data never leaks to the next.

## Scope

- Debug-build wiring to the Functions emulator (`:remote-datasource` factory and build config, debug network security config in `:app`).
- Plaid Link SDK dependency and initialization (`gradle/libs.versions.toml`, `:app/build.gradle.kts`, `CreamApplication`).
- `app/.../screens/institutions/` (including `PlaidLinkLauncher.kt` and `PlaidLinkResult.kt`) and `app/.../screens/accountdetail/`.
- `MainRoute` entries and `MainNavGraph` registration for both screens.
- `createLinkToken` on `InstitutionRepository` if the screen needs it.
- Clearing the local institution cache on sign-out (`:local-datasource` institution DAO and datasource, `InstitutionRepository`, and the sign-out path).

## Out of scope

- Transaction categorization.
- Production Plaid access against real institutions.

## Done when

- Signing out leaves the local institution cache empty, covered by a test.
- The Plaid Sandbox checklist in CREAM-007 passes end to end.

---
id: CREAM-003
title: Add a Compose Plaid Link launcher
status: todo
created: 2026-10-19
depends-on: [CREAM-001]
---

A Compose helper that launches Plaid Link with a link token and returns an SDK-free result, so ViewModels never import Plaid types.

## Scope

- `app/.../screens/institutions/PlaidLinkLauncher.kt` — `rememberPlaidLinkLauncher(onResult: (PlaidLinkResult) -> Unit)` returning a handle with `launch(linkToken: String)`. Built on `rememberLauncherForActivityResult` with the SDK's activity-result contract.
- `app/.../screens/institutions/PlaidLinkResult.kt` — sealed: `Success(publicToken, institutionId, institutionName)`, `Cancelled`, `Error(message)`. Translate the SDK's result into this type at the UI boundary.

## Out of scope

- The Institutions screen and ViewModel that use it (CREAM-004).

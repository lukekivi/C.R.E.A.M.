---
id: CREAM-004
title: Build the Institutions screen
status: todo
created: 2026-10-19
depends-on: [CREAM-002, CREAM-003]
---

A screen listing every linked institution with its accounts and balances grouped underneath. The user can link a new institution, pull to refresh, and disconnect one.

## Scope

- `MainRoute.Institutions`, reachable from a button on `HomeScreen`.
- `InstitutionsViewModel` (companion factory per the `cream-viewmodel` pattern) backed by `InstitutionRepository`:
  - Collects `observeInstitutionsWithAccounts()`.
  - `linkInstitution()` — obtains a link token, hands it to the launcher via a one-shot event; on `PlaidLinkResult.Success` calls `institutionRepository.linkInstitution(...)`.
  - `refresh()` — `refreshInstitutions()` then `syncTransactions(itemId)` per institution.
  - `disconnect(itemId)` — with a confirmation dialog.
  - UiState covers loading, refreshing, linking, and error.
- `InstitutionsScreen` — grouped list (institution header → account rows with name, mask, type, balance), FAB to link, `PullToRefreshBox`, overflow menu on the header for disconnect, empty state.
- Register the entry in `MainNavGraph`.

## Out of scope

- Account detail navigation target (CREAM-005) — account rows can be non-clickable or route to a stub until then.

## Notes

- `InstitutionRepository` has no `createLinkToken` today; the remote datasource does. Adding it to the repository is likely part of this ticket.
- Currency: all target institutions are USD. Confirm whether `isoCurrencyCode`-aware formatting is wanted now or later.

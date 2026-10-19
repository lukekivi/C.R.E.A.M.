---
id: CREAM-005
title: Build the Account detail screen
status: todo
created: 2026-10-19
depends-on: [CREAM-004]
---

Tapping an account on the Institutions screen opens a detail screen with the current balance and recent transactions.

## Scope

- `MainRoute.AccountDetail(accountId)`; navigate from account rows on the Institutions screen, back returns there.
- `AccountDetailViewModel` (companion factory) collecting `observeAccount(accountId)` and `observeTransactions(accountId)`. `refresh()` resolves the account's `itemId` and calls `syncTransactions(itemId)`.
- `AccountDetailScreen` — large current balance, account name + mask + type, transactions grouped by date, pull to refresh, loading/error/empty states.
- Register the entry in `MainNavGraph`.

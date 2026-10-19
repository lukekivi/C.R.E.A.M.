---
id: CREAM-007
title: Manual Plaid Sandbox end-to-end test
status: todo
created: 2026-10-19
depends-on: [CREAM-004, CREAM-005, CREAM-006]
---

Walk the full flow against Plaid Sandbox on the emulators and fix anything that breaks. Sandbox credentials: institution "First Platypus Bank", `user_good` / `pass_good`.

## Checklist

1. Cold start, signed in → Home → Institutions → empty state.
2. FAB → Plaid Link → First Platypus Bank → `user_good` / `pass_good` → accounts appear grouped under the institution.
3. Tap a checking account → detail shows balance and sandbox transactions.
4. Pull to refresh on detail → no duplicates.
5. Force-stop, reopen → cached data shows immediately; refresh runs async.
6. Disconnect → confirm → list updates, Firestore item doc deleted, cached accounts/transactions for that item gone.
7. Sign out → sign in as a different user → no previous-user data visible.

## Out of scope

- Production Trial verification against real institutions (needs Plaid production approval).

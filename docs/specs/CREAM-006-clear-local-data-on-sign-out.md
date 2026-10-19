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

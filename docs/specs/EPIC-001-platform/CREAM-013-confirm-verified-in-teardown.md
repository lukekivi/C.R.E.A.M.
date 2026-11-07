---
id: CREAM-013
title: Move confirmVerified into tearDown in existing tests
status: todo
created: 2026-11-07
depends-on: [CREAM-006]
---

STYLE.md's "Confirm verified" rule changed in CREAM-006's PR: a test class now calls `confirmVerified(...)` on its class-level collaborators once, in its top-level `@AfterEach` `tearDown`, instead of at the end of every test. `HomeViewModelTest` follows the new rule. The test classes written before it still confirm in each test.

## Scope

- `local-datasource/.../institution/InstitutionLocalDataSourceImplTest.kt`: 12 tests each call `confirmVerified(dao, transformer)`.
- `data/.../repository/institution/InstitutionRepositoryImplTest.kt`: 15 tests each call `confirmVerified(remote, local)`.
- `remote-datasource/.../institution/InstitutionRemoteDataSourceImplTest.kt`: 12 tests each call `confirmVerified(functions, callable, transformer)`.

For each class, add an `@AfterEach fun tearDown()` that confirms the class-level collaborators, and remove the per-test calls. In `InstitutionRemoteDataSourceImplTest`, `callable` is created per test by `setupCallable`/`setupFailingCallable`, so each test keeps `confirmVerified(callable)` at the end of its Verify section (per the rule's exception).

All three modules' unit tests pass.

## Out of scope

- Any other test change. Verify blocks stay as they are.

## Notes

- Check for any test class added after this ticket was written: `grep -rl confirmVerified --include='*Test.kt'`.

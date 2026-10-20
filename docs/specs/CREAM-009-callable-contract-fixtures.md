---
id: CREAM-009
title: Pin the callable response contract on both sides
status: todo
created: 2026-10-20
---

CREAM-002's smoke test found that the client parsed transaction ids from `id` while `syncTransactions` returns `transactionId`. The client unit tests passed because their fixtures were hand-written copies of the response shape with the same wrong key. Nothing ties the fixtures in `remote-datasource/src/test` to what `functions/src` actually returns, so the next field rename on either side can slip through the same way.

## Scope

- One source of truth per callable response shape (`createLinkToken`, `exchangePublicToken`, `listInstitutions`, `syncTransactions`, `disconnectInstitution`), e.g. JSON fixture files checked into the repo.
- Server side: a test that asserts each callable's output shaping (e.g. `shape()` in `syncTransactions.ts`) produces exactly the fixture's keys. `functions/` has no test runner yet, so add one (and an `npm test` script).
- Client side: `InstitutionTransformerTest` loads the same fixtures instead of inline maps. (`InstitutionRemoteDataSourceImplTest` mocks the transformer, so it needs no payload fixtures.)

## Out of scope

- Generating types from a schema (OpenAPI/protobuf). Revisit if the callable surface grows.
- CI.

## Notes

- Keep the server test and the fixtures caller-agnostic: they describe the callable's output, not any one client.
- The fixtures need a location both Gradle tests and the TS tests can read (e.g. a top-level `contracts/` directory), since `functions/` and the Android modules don't share a source tree.

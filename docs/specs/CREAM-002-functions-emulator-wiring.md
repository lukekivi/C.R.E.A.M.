---
id: CREAM-002
title: Point debug builds at the Firebase emulators and verify the callables
status: todo
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

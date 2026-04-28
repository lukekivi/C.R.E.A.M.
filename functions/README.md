# CREAM Functions

Firebase Cloud Functions backend for the CREAM Android app. Holds Plaid access tokens server-side and exposes five callables. Plaid access tokens never cross back to the client; the only handle the caller ever sees is an opaque `itemId`.

## File layout

```
functions/
├── package.json, tsconfig.json, .gitignore
└── src/
    ├── index.ts                    re-exports the five callables
    ├── plaid.ts                    Plaid client + four secret declarations
    ├── firestore.ts                Admin SDK init + ItemDoc + itemRef helper
    ├── auth.ts                     requireAuth() — every callable's first line
    ├── errors.ts                   Plaid error → HttpsError mapping
    ├── createLinkToken.ts          callable
    ├── exchangePublicToken.ts      callable
    ├── listInstitutions.ts         callable
    ├── syncTransactions.ts         callable
    └── disconnectInstitution.ts    callable
```

## Prerequisites

- **Node 20+**. The deployed runtime is Node 20 (pinned via `package.json` → `engines.node`); local may be newer.
- **`firebase-tools` CLI**: `npm install -g firebase-tools`.
- A **Plaid dashboard account** with Sandbox keys. Sandbox is free and instant — no approval gate. Production Trial keys (free up to 10 Items) require a separate approval step in the Plaid dashboard.

## One-time setup

1. Install dependencies:
   ```sh
   npm install
   ```
2. Authenticate the CLI:
   ```sh
   firebase login
   ```
3. Set the four Plaid secrets (the CLI will prompt you for each value):
   ```sh
   firebase functions:secrets:set PLAID_CLIENT_ID
   firebase functions:secrets:set PLAID_SECRET_SANDBOX
   firebase functions:secrets:set PLAID_SECRET_PRODUCTION    # placeholder ok until Production Trial is granted
   firebase functions:secrets:set PLAID_ENV                  # value: "sandbox" or "production"
   ```

The same code path serves both environments; only `PLAID_ENV` flips between them.

## Local emulator workflow

The deployed code reads secrets from Firebase. The emulator reads them from a local `.secret.local` file (gitignored) instead.

1. Create `functions/.secret.local` with one `KEY=VALUE` pair per line:
   ```
   PLAID_CLIENT_ID=your_client_id_here
   PLAID_SECRET_SANDBOX=your_sandbox_secret_here
   PLAID_SECRET_PRODUCTION=placeholder
   PLAID_ENV=sandbox
   ```
2. Run the emulators:
   ```sh
   npm run serve
   ```
   This builds (`tsc`) and starts the Functions, Firestore, and Auth emulators. Default ports: Functions `5001`, Firestore `8080`, Auth `9099`. The Emulator UI is at `http://localhost:4000`.

3. The Android emulator reaches the host machine via `10.0.2.2`. Debug builds wire `FirebaseFunctions.useEmulator("10.0.2.2", 5001)` (Phase 3).

### Manually invoking a callable

Callable functions expose an HTTP endpoint at:

```
http://localhost:5001/<projectId>/us-central1/<callableName>
```

The body must be wrapped in `{ "data": ... }`. Auth-required callables also need a `Bearer` token from the Auth emulator. To exercise without Auth, use the Emulator UI's Functions tab — it mints a synthetic auth context for you.

## Deploying

```sh
npm run deploy
```

This runs the `predeploy` step (`tsc`) and pushes the compiled `lib/` to Firebase.

## Callable wire formats

All inputs and outputs are JSON. The Firebase callable protocol wraps payloads as `{ "data": <input> }` on the wire and `{ "result": <output> }` on the response; the bodies below are the *unwrapped* shapes — what your client SDK serializes/deserializes.

Every callable rejects with `HttpsError("unauthenticated")` if `request.auth` is missing.

### `createLinkToken`

Input:
```json
{}
```
Output:
```json
{ "linkToken": "link-sandbox-abc123…" }
```

Notes: short-lived link token tied to the caller's uid. One-shot — used to open Plaid Link.

### `exchangePublicToken`

Input:
```json
{
  "publicToken":     "public-sandbox-abc…",
  "institutionId":   "ins_109508",
  "institutionName": "First Platypus Bank"
}
```
Output:
```json
{ "itemId": "xYz9…", "institutionName": "First Platypus Bank" }
```

Notes: writes a Firestore doc at `users/{uid}/items/{itemId}` containing the long-lived access token. Re-linking the same Item overwrites the doc.

### `listInstitutions`

Input:
```json
{}
```
Output:
```json
{
  "institutions": [
    {
      "itemId":          "xYz9…",
      "institutionId":   "ins_109508",
      "institutionName": "First Platypus Bank",
      "accounts": [
        {
          "accountId": "acc_1",
          "name":      "Plaid Checking",
          "mask":      "0000",
          "type":      "depository",
          "subtype":   "checking",
          "balance": {
            "available":       100.0,
            "current":         110.0,
            "isoCurrencyCode": "USD"
          }
        }
      ]
    }
  ]
}
```

Notes: balances are fetched live from Plaid on every call. `Promise.all` fans the per-institution calls out in parallel. Access tokens are stripped from the response.

### `syncTransactions`

Input:
```json
{
  "itemId": "xYz9…",
  "cursor": null
}
```
`cursor` is a string from a previous response, or `null` for the first sync. See [ADR 0001](../docs/decisions/0001-cursor-owned-by-caller.md) for why the cursor is caller-owned.

Output:
```json
{
  "added":    [ { "transactionId": "t_1", "accountId": "acc_1", "amount": 4.5, "isoCurrencyCode": "USD", "date": "2026-04-27", "name": "Coffee", "merchantName": "Blue Bottle", "pending": false, "category": "Food and Drink" } ],
  "modified": [],
  "removed":  [ { "transactionId": "t_2" } ],
  "cursor":   "next-cursor-xyz"
}
```

Notes: server drains Plaid's `has_more` pagination internally; the response is one combined delta. Only the listed transaction fields are passed through.

### `disconnectInstitution`

Input:
```json
{ "itemId": "xYz9…" }
```
Output:
```json
{ "ok": true }
```

Notes: revokes the access token at Plaid (`itemRemove`), then deletes the Firestore doc. If Plaid reports `ITEM_NOT_FOUND` (already revoked elsewhere) the Firestore doc is still deleted — caller-visible state must reach "gone."

## Plaid Sandbox test credentials

- Username: `user_good`
- Password: `pass_good`
- MFA (when prompted): `1234`
- Demo institution: `ins_109508` (First Platypus Bank)

To mint a public token without going through Plaid Link (handy for smoke-testing `exchangePublicToken` from the emulator):

```sh
curl -X POST https://sandbox.plaid.com/sandbox/public_token/create \
  -H 'Content-Type: application/json' \
  -d '{
    "client_id":         "<PLAID_CLIENT_ID>",
    "secret":            "<PLAID_SECRET_SANDBOX>",
    "institution_id":    "ins_109508",
    "initial_products":  ["transactions"]
  }'
```

The returned `public_token` is shape-identical to one Plaid Link would produce.

## Known gaps

- **Webhooks** are out of scope for this phase. A `webhookHandler` Function that receives Plaid `TRANSACTIONS` webhooks and flags items for sync is a planned follow-up.
- **Production deploy automation** (CI/CD) is out of scope. Manual `npm run deploy` is the workflow.
- **Automated test harness** (`npm test` against the emulator) is not included. The Manual Verification section of the phase plan is the substitute until added.

## Architecture decisions

- [ADR 0001 — Plaid sync cursor is owned by the caller](../docs/decisions/0001-cursor-owned-by-caller.md). Why `syncTransactions` takes the cursor as input rather than reading it from Firestore. Read this before considering any change to the cursor's location.

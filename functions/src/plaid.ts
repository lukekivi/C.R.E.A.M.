/**
 * Plaid client wiring.
 *
 * Holds the four secrets the backend needs (`PLAID_CLIENT_ID`,
 * `PLAID_SECRET_SANDBOX`, `PLAID_SECRET_PRODUCTION`, `PLAID_ENV`) and
 * builds a single shared `PlaidApi` instance per warm container.
 *
 * Secrets are declared at module top with `defineSecret` so they can be
 * listed on each callable's options (`onCall({ secrets: [...] }, ...)`).
 * Their values are only readable inside a handler via `.value()`.
 */

import {defineSecret} from "firebase-functions/params";
import {Configuration, PlaidApi, PlaidEnvironments} from "plaid";

/** Plaid client ID (same value across environments). */
export const PLAID_CLIENT_ID = defineSecret("PLAID_CLIENT_ID");

/** Plaid secret for the Sandbox environment. */
export const PLAID_SECRET_SANDBOX = defineSecret("PLAID_SECRET_SANDBOX");

/** Plaid secret for the Production environment. */
export const PLAID_SECRET_PRODUCTION = defineSecret("PLAID_SECRET_PRODUCTION");

/** Environment selector. Allowed values: `sandbox`, `production`. */
export const PLAID_ENV = defineSecret("PLAID_ENV");

/** Convenience array — every callable lists this on its `onCall` options. */
export const PLAID_SECRETS = [
  PLAID_CLIENT_ID,
  PLAID_SECRET_SANDBOX,
  PLAID_SECRET_PRODUCTION,
  PLAID_ENV,
] as const;

let cachedClient: PlaidApi | null = null;

/**
 * Returns a memoized Plaid API client.
 *
 * Must be called from within a callable handler so the secret values are
 * available. The first call in a warm container builds the client;
 * subsequent calls reuse it.
 */
export function getPlaidClient(): PlaidApi {
  if (cachedClient) {
    return cachedClient;
  }

  const env = PLAID_ENV.value().toLowerCase();
  const basePath =
    env === "production" ?
      PlaidEnvironments.production :
      PlaidEnvironments.sandbox;
  const plaidSecret =
    env === "production" ?
      PLAID_SECRET_PRODUCTION.value() :
      PLAID_SECRET_SANDBOX.value();

  const configuration = new Configuration({
    basePath,
    baseOptions: {
      headers: {
        "PLAID-CLIENT-ID": PLAID_CLIENT_ID.value(),
        "PLAID-SECRET": plaidSecret,
        "Plaid-Version": "2020-09-14",
      },
    },
  });

  cachedClient = new PlaidApi(configuration);
  return cachedClient;
}

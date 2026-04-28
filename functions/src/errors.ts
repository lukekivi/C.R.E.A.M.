/**
 * Plaid error helpers.
 *
 * Plaid SDK errors are axios-shaped — the API error object lives at
 * `err.response.data`. Callables surface those as `HttpsError("internal",
 * "<op> failed: <ERROR_CODE>")` so the Android client sees a stable code
 * without leaking PII (Plaid `error_message` can quote user input).
 */

import {HttpsError} from "firebase-functions/v2/https";

/**
 * Extracts the Plaid `error_code` (e.g. `ITEM_NOT_FOUND`,
 * `INVALID_ACCESS_TOKEN`) from a thrown SDK error, or `null` if the error
 * doesn't have the expected shape.
 */
export function extractPlaidErrorCode(err: unknown): string | null {
  const candidate = (err as {response?: {data?: {error_code?: unknown}}})
    ?.response?.data?.error_code;
  return typeof candidate === "string" ? candidate : null;
}

/**
 * Wraps a Plaid SDK error as an `HttpsError` with the Plaid error code in
 * the message.
 */
export function plaidError(operation: string, err: unknown): HttpsError {
  const code = extractPlaidErrorCode(err) ?? "PLAID_UNKNOWN";
  return new HttpsError("internal", `${operation} failed: ${code}`);
}
